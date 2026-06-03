package org.mifos.workflow.spi;

import org.mifos.workflow.core.model.ActiveProcess;
import org.mifos.workflow.core.model.DeploymentInfo;
import org.mifos.workflow.core.model.DeploymentInfoEnhanced;
import org.mifos.workflow.core.model.DeploymentResource;
import org.mifos.workflow.core.model.DeploymentResult;
import org.mifos.workflow.core.model.ProcessCompletionStatus;
import org.mifos.workflow.core.model.ProcessDefinitionInfo;
import org.mifos.workflow.core.model.ProcessHistoryInfo;
import org.mifos.workflow.core.model.ProcessInstance;
import org.mifos.workflow.core.model.ProcessStatus;
import org.mifos.workflow.core.model.ProcessVariables;
import org.mifos.workflow.core.model.TaskInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * WorkflowService SPI (Service Provider Interface).
 *
 * This is the single contract that all application code depends on.
 * No class outside an adapter module may ever implement this interface
 * using engine-specific imports.
 *
 * Two concrete implementations exist:
 *   - FlowableWorkflowService  (mifos-workflow-adapter-flowable)
 *   - ConductorWorkflowService (mifos-workflow-adapter-conductor)
 *
 * Spring selects the correct implementation at startup based on:
 *   workflow.engine.type=flowable   (or conductor)
 *
 * SECURITY: This interface contains no authentication logic.
 * Authentication is enforced by the concrete adapter implementations
 * and by the controllers via FineractAuthService before calling here.
 */
public interface WorkflowService {

    // ─────────────────────────────────────────────
    // PROCESS LIFECYCLE
    // ─────────────────────────────────────────────

    /**
     * Start a new workflow process instance.
     * e.g. "client-onboarding", "loan-origination"
     *
     * @param processDefinitionKey the BPMN process key
     * @param variables            input data for the process
     * @return the started ProcessInstance with its ID
     */
    ProcessInstance startProcess(String processDefinitionKey,
                                 Map<String, Object> variables);

    /**
     * Complete a human task (e.g. manager approves a loan).
     * In Flowable this completes a UserTask.
     * In Conductor this updates a HUMAN_TASK to COMPLETED.
     *
     * @param taskId    the task identifier
     * @param variables outcome variables from the human decision
     */
    void completeTask(String taskId, Map<String, Object> variables);

    /**
     * Terminate a running process instance with a reason.
     * Used for manual cancellation via the REST API.
     *
     * @param processInstanceId the process to terminate
     * @param reason            human-readable reason for termination
     */
    void terminateProcess(String processInstanceId, String reason);

    // ─────────────────────────────────────────────
    // TASK QUERIES
    // ─────────────────────────────────────────────

    /**
     * Get all pending (uncompleted) tasks for a user.
     *
     * @param userId the assignee user ID
     * @return list of pending TaskInfo objects
     */
    List<TaskInfo> getPendingTasks(String userId);

    /**
     * Get all pending tasks for a specific process instance.
     *
     * @param processInstanceId the process instance ID
     * @return list of pending TaskInfo objects for that process
     */
    List<TaskInfo> getPendingTasksForProcess(String processInstanceId);

    // ─────────────────────────────────────────────
    // PROCESS VARIABLE OPERATIONS
    // ─────────────────────────────────────────────

    /**
     * Get all current variables of a running process.
     *
     * @param processInstanceId the running process ID
     * @return ProcessVariables wrapper around the variable map
     */
    ProcessVariables getProcessVariables(String processInstanceId);

    /**
     * Get variables from a completed (historic) process.
     *
     * @param processInstanceId the historic process ID
     * @return ProcessVariables from the history store
     */
    ProcessVariables getHistoricProcessVariables(String processInstanceId);

    /**
     * Get variables associated with a specific task.
     *
     * @param taskId the task ID
     * @return ProcessVariables for that task
     */
    ProcessVariables getTaskVariables(String taskId);

    /**
     * Set or update variables on a running process.
     *
     * @param processInstanceId the process to update
     * @param variables         the variables to set
     */
    void setProcessVariables(String processInstanceId,
                             Map<String, Object> variables);

    // ─────────────────────────────────────────────
    // PROCESS STATUS & MONITORING
    // ─────────────────────────────────────────────

    /**
     * Get the current execution status of a process.
     *
     * @param processInstanceId the process ID
     * @return ProcessStatus with current activity and state
     */
    ProcessStatus getProcessStatus(String processInstanceId);

    /**
     * Get the final completion status of a finished process.
     *
     * @param processInstanceId the process ID
     * @return ProcessCompletionStatus with outcome and end details
     */
    ProcessCompletionStatus getProcessCompletionStatus(
            String processInstanceId);

    /**
     * Get all currently running process instances.
     *
     * @return list of ActiveProcess objects
     */
    List<ActiveProcess> getActiveProcesses();

    /**
     * Get detailed info about all deployed process definitions.
     *
     * @return list of ProcessDefinitionInfo objects
     */
    List<ProcessDefinitionInfo> getProcessDefinitionsInfo();

    /**
     * Get historical records of all process executions.
     *
     * @return list of ProcessHistoryInfo audit records
     */
    List<ProcessHistoryInfo> getProcessHistoryInfo();

    // ─────────────────────────────────────────────
    // DEPLOYMENT MANAGEMENT
    // ─────────────────────────────────────────────

    /**
     * Deploy a BPMN process definition from an input stream.
     *
     * @param processDefinition the BPMN XML input stream
     * @param filename          the original filename (e.g. loan-origination.bpmn20.xml)
     * @return DeploymentResult with deployment ID and success status
     */
    DeploymentResult deployProcess(InputStream processDefinition,
                                   String filename);

    /**
     * Get all deployments registered with the engine.
     *
     * @return list of DeploymentInfo objects
     */
    List<DeploymentInfo> getDeployments();

    /**
     * Get enhanced details about a specific deployment.
     *
     * @param deploymentId the deployment ID
     * @return DeploymentInfoEnhanced with full metadata
     */
    DeploymentInfoEnhanced getDeploymentInfo(String deploymentId);

    /**
     * Get all resource files inside a deployment.
     *
     * @param deploymentId the deployment ID
     * @return list of DeploymentResource descriptors
     */
    List<DeploymentResource> getDeploymentResources(String deploymentId);

    /**
     * Get the raw bytes of a specific resource inside a deployment.
     * Used to retrieve BPMN XML content for inspection.
     *
     * @param deploymentId the deployment ID
     * @param resourceName the resource filename
     * @return byte array of the resource content
     */
    byte[] getDeploymentResource(String deploymentId,
                                 String resourceName);

    /**
     * Delete a deployment and all its process definitions.
     *
     * SECURITY: This is a destructive operation. Access to this
     * endpoint should be restricted to admin roles only at the
     * controller layer.
     *
     * @param deploymentId the deployment to delete
     */
    void deleteDeployment(String deploymentId);
}