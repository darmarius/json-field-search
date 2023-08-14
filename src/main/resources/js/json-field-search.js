let $ = jQuery.noConflict();
window.onbeforeunload = null;

$(document).ready(function () {
    AJS.$("#field-select").auiSelect2();
    $("#resulttableblock").hide();
    AJS.$(document).on('click', '#find_entry', function () {
        var that = this;
        $("#result-table tbody").empty();
        $("#resulttableblock").hide();
        if (!that.isBusy()) {
            that.busy();
            let inputField = $("#field-select").val().replace(/"/g, '');
            let inputJsonKey = $("#inputJsonKey").val();
            let inputPattern = $("#inputPattern").val();
            let jqlQuery = `issue in textContainsJson("${inputField}", "${inputJsonKey}", "${inputPattern}")`;
            let url = `/rest/api/2/search`;
            $.ajax({
                url: url,
                type: 'get', 
                data: { jql: jqlQuery }, 
                success: function (response) {
                    if(response.issues.length == 0){
                        alert("Ничего не найдено");
                    }
                    else {
                        for (const issue of response.issues) {
                            let assignee = 'null'
                            if(issue.fields.assignee) assignee = issue.fields.assignee.displayName
                            let row = `
                                <tr>
                                    <td><a href="${AJS.contextPath()}/browse/${issue.key}" target="_blank">${issue.key}</a></td>
                                    <td>${issue.fields.project.name}</td>
                                    <td>${issue.fields.issuetype.name}</td>
                                    <td>${issue.fields.creator.displayName}</td>
                                    <td>${assignee}</td>
                                    <td>${issue.fields.summary}</td>
                                    <td>${issue.fields[inputField]}</td>
                                </tr>
                            `;
                            $("#result-table tbody").append(row);
                        }

                        $("#resulttableblock").show();
                    }
                },
                error: function (error) {
                    alert("Ничего не найдено");
                }
            });
            that.idle();
        }
    });
});