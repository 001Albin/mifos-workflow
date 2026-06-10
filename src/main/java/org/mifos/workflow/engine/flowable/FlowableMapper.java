


    public ProcessInstance mapToProcessInstance(org.flowable.engine.runtime.ProcessInstance flowableInstance) {
        return ProcessInstance.builder()
                .id(flowableInstance.getId())
                .processDefinitionId(flowableInstance.getProcessDefinitionId())
                .businessKey(flowableInstance.getBusinessKey())
                .status(flowableInstance.isEnded() ? "completed" : "active")
                .startTime(LocalDateTime.ofInstant(flowableInstance.getStartTime().toInstant(), ZoneId.systemDefault()))
                .build();
    }


    public DeploymentInfo mapToDeploymentInfo(Deployment deployment) {
        return DeploymentInfo.builder()
                .id(deployment.getId())
                .name(deployment.getName())
                .deploymentTime(LocalDateTime.ofInstant(deployment.getDeploymentTime().toInstant(), ZoneId.systemDefault()))
                .build();
    }


    public TaskInfo mapToTaskInfo(Task task) {
        return TaskInfo.builder()
                .taskId(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .assignee(task.getAssignee())
                .processId(task.getProcessInstanceId())
                .processDefinitionId(task.getProcessDefinitionId())
                .createTime(LocalDateTime.ofInstant(task.getCreateTime().toInstant(), ZoneId.systemDefault()))
                .dueDate(task.getDueDate() != null ? 
                    LocalDateTime.ofInstant(task.getDueDate().toInstant(), ZoneId.systemDefault()) : null)
                .priority(task.getPriority())
                .build();
    }


    public HistoricProcessInstance mapToHistoricProcessInstance(org.flowable.engine.history.HistoricProcessInstance historicInstance) {
        return HistoricProcessInstance.builder()
                .id(historicInstance.getId())
                .processDefinitionId(historicInstance.getProcessDefinitionId())
                .businessKey(historicInstance.getBusinessKey())
                .startTime(LocalDateTime.ofInstant(historicInstance.getStartTime().toInstant(), ZoneId.systemDefault()))
                .endTime(historicInstance.getEndTime() != null ? 
                    LocalDateTime.ofInstant(historicInstance.getEndTime().toInstant(), ZoneId.systemDefault()) : null)
                .durationInMillis(historicInstance.getDurationInMillis())
                .outcome(historicInstance.getEndActivityId())
                .build();
    }
} 