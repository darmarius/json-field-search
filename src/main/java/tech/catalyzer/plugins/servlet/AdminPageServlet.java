package tech.catalyzer.plugins.servlet;

import com.atlassian.templaterenderer.TemplateRenderer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

public class AdminPageServlet extends HttpServlet {
    @ComponentImport
    private TemplateRenderer templateRenderer;

    @Inject
    public AdminPageServlet(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("textFields", extractCustomFields());
        templateRenderer.render("/templates/configure.vm", paramMap, resp.getWriter());
    }

    public JsonArray extractCustomFields() {
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        List<CustomField> customFields = customFieldManager.getCustomFieldObjects();

        JsonArray customFieldsMap = new JsonArray();

        for (CustomField customField : customFields) {
            if ("com.atlassian.jira.plugin.system.customfieldtypes:textarea".equals(customField.getCustomFieldType().getKey())) {
                JsonObject fieldInfo = new JsonObject();
                fieldInfo.addProperty("customFieldId", customField.getId());
                fieldInfo.addProperty("customFieldName", customField.getName());

                customFieldsMap.add(fieldInfo);
            }
        }
        return customFieldsMap;
    }
    
}