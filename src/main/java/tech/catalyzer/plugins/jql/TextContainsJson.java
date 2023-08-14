package tech.catalyzer.plugins.jql;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.jql.operand.QueryLiteral;
import com.atlassian.jira.jql.parser.JqlParseException;
import com.atlassian.jira.jql.parser.JqlQueryParser;
import com.atlassian.jira.jql.query.QueryCreationContext;
import com.atlassian.jira.plugin.jql.function.AbstractJqlFunction;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.MessageSet;
import com.atlassian.jira.util.MessageSetImpl;
import com.atlassian.query.clause.TerminalClause;
import com.atlassian.query.operand.FunctionOperand;
import com.atlassian.query.operand.Operand;
import com.atlassian.query.Query;
import com.atlassian.query.QueryImpl;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.JiraDataType;
import com.atlassian.jira.JiraDataTypes;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class TextContainsJson extends AbstractJqlFunction {

    @ComponentImport
    private final CustomFieldManager customFieldManager;
    @ComponentImport
    private final JqlQueryParser jqlQueryParser;
    @ComponentImport
    private final SearchService searchService;

    private static final Logger log = LoggerFactory.getLogger(TextContainsJson.class);

    @Inject
    public TextContainsJson(CustomFieldManager customFieldManager, JqlQueryParser jqlQueryParser, SearchService searchService) {
        this.customFieldManager = customFieldManager;
        this.jqlQueryParser = jqlQueryParser;
        this.searchService = searchService;
    }

    public MessageSet validate(ApplicationUser applicationUser, FunctionOperand functionOperand, TerminalClause terminalClause) {
        MessageSetImpl messageSet = new MessageSetImpl();
        Optional<Arguments> arguments = getArguments(functionOperand);
        if (!arguments.isPresent()) {
            messageSet.addErrorMessage("Invalid arguments, use this snippet textContainsJson(\"fieldName\",\"key\",\"pattern\")");
            return messageSet;
        }
        return validateNumberOfArgs(functionOperand, getMinimumNumberOfExpectedArguments());
    }

    public List<QueryLiteral> getValues(QueryCreationContext queryCreationContext, FunctionOperand functionOperand, TerminalClause terminalClause) {
        Optional<Arguments> arguments = getArguments(functionOperand);
        if (arguments.isPresent()) {
            Arguments arg = arguments.get();
            return getIssuesByJql(queryCreationContext.getApplicationUser(), arg)
                    .stream()
                    .filter(issue -> arg.isIssueContainValue(issue))
                    .map(issue -> new QueryLiteral((Operand) functionOperand, issue.getId()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<Issue> getIssuesByJql(ApplicationUser currentUser, Arguments arguments) {
        String jql = '"' + arguments.getFieldName() + '"' + " is not empty";

        List<Issue> resultList = new ArrayList<>();
        Query query = new QueryImpl();
        try {
            query = jqlQueryParser.parseQuery(jql);
        } catch (JqlParseException e) {
            log.error(jql, e);
        }
        try {
            SearchResults<Issue> searchResults = searchService.search(currentUser, query, PagerFilter.getUnlimitedFilter());
            List<Issue> jqlResult = searchResults.getResults();
            resultList.addAll(jqlResult);
        } catch (SearchException e) {
            log.error(jql, e);
        }
        return resultList;
    }

    private Optional<Arguments> getArguments(FunctionOperand functionOperand) {
        if (!functionOperand.getArgs().isEmpty()) {
            String fieldName = functionOperand.getArgs().get(0);
            CustomField customField = getCustomField(fieldName);
            Optional<String> jsonKey = getJsonKey(functionOperand);
            Optional<Pattern> regex = getRegex(functionOperand);

            if (jsonKey.isPresent() && regex.isPresent() && customField != null) {
                return Optional.of(new Arguments(jsonKey.get(), regex.get(), customField));
            }
        }
        return Optional.empty();
    }

    private CustomField getCustomField(String fieldName) {
        Collection<CustomField> fields = this.customFieldManager.getCustomFieldObjectsByName(fieldName);
        if (fields == null || fields.isEmpty()) {
            CustomField customFieldById = customFieldManager.getCustomFieldObject(fieldName);
            if (customFieldById != null) {
                return customFieldById;
            }
            return null;
        }
        return fields.iterator().next();
    }

    private Optional<String> getJsonKey(FunctionOperand functionOperand) {
        if (functionOperand.getArgs().size() > 1) {
            return Optional.of(functionOperand.getArgs().get(1));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Pattern> getRegex(FunctionOperand functionOperand) {
        if (functionOperand.getArgs().size() > 2) {
            try {
                return Optional.of(Pattern.compile(functionOperand.getArgs().get(2)));
            } catch (PatternSyntaxException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public JiraDataType getDataType() {
        return JiraDataTypes.ISSUE;
    }

    public int getMinimumNumberOfExpectedArguments() {
        return 3;
    }

    private class Arguments {
        private final CustomField customField;
        private final String jsonKey;
        private final Pattern pattern;

        Arguments(String jsonKey, Pattern pattern, CustomField customField) {
            this.jsonKey = jsonKey;
            this.pattern = pattern;
            this.customField = customField;
        }

        String getFieldName() {
            return this.customField.getFieldName();
        }

        Pattern getPattern() {
            return this.pattern;
        }

        boolean isIssueContainValue(Issue issue) {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(this.getFieldValue(issue));
            List<String> jsonValuesByKey = findAllJsonValuesByKey(jsonElement);

            if (jsonValuesByKey.isEmpty()) {
                return false;
            }

            List<String> matches = jsonValuesByKey
                    .stream()
                    .filter(this.getPattern().asPredicate())
                    .collect(Collectors.toList());

            return !matches.isEmpty();
        }

        List<String> findAllJsonValuesByKey(JsonElement jsonElement) {
            List<String> values = new ArrayList<>();
            findAllJsonValuesByKey(jsonElement, values);
            return values;
        }

        void findAllJsonValuesByKey(JsonElement jsonElement, List<String> values) {
            if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                if (jsonObject.has(this.jsonKey)) {
                    values.add(jsonObject.get(this.jsonKey).getAsString());
                }
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    JsonElement nestedElement = entry.getValue();
                    findAllJsonValuesByKey(nestedElement, values);
                }
            } else if (jsonElement.isJsonArray()) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (JsonElement arrayElement : jsonArray) {
                    findAllJsonValuesByKey(arrayElement, values);
                }
            }
        }

        String getFieldValue(Issue issue) {
            Object customFieldValue = issue.getCustomFieldValue(this.customField);
            if (customFieldValue == null || customFieldValue.toString().isEmpty()) {
                return "";
            } else {
                return customFieldValue.toString();
            }
        }
    }
}
