package com.kibocommerce.bpm.fulfillment.hidden;

import org.jbpm.test.JbpmJUnitBaseTestCase;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.kie.api.task.model.Status.Reserved;

public class AFG_Custom_STH_Process_Test extends JbpmJUnitBaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(AFG_Custom_STH_Process_Test.class);

    private RuntimeManager runtimeManager;
    private RuntimeEngine runtimeEngine;
    private KieSession kieSession;
    private TaskService taskService;

    public AFG_Custom_STH_Process_Test() {
        super(true, false);
    }

    @Before
    public void init() {
        runtimeManager = createRuntimeManager("com/kibocommerce/bpm/fulfillment/hidden/AFG_Custom_STH_Process.bpmn");
        runtimeEngine = getRuntimeEngine(null);
        kieSession = runtimeEngine.getKieSession();
        taskService = runtimeEngine.getTaskService();
    }

    @Test
    public void acceptShipment_true() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);

        assertNodeActive(wpi.getId(), kieSession, "Validate Items In Stock");
        assertEquals("ACCEPTED_SHIPMENT", wpi.getVariable("currentState"));
    }

    @Test
    public void acceptShipment_false() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, false);

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("REASSIGN_SHIPMENT", wpi.getVariable("currentState"));
    }

    @Test
    public void validateStock_inStock() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");

        assertNodeActive(wpi.getId(), kieSession, "Wait for Payment Confirmation");
        assertEquals("INVENTORY_AVAILABLE", wpi.getVariable("currentState"));
    }

    @Test
    public void validateStock_partialStock() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "PARTIAL_STOCK");

        assertNodeActive(wpi.getId(), kieSession, "Wait for Payment Confirmation");
        assertEquals("PARTIAL_INVENTORY_NOPE", wpi.getVariable("currentState"));
    }

    @Test
    public void validateStock_noStock() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "NO_STOCK");

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("REASSIGN_SHIPMENT", wpi.getVariable("currentState"));
    }

    @Test
    public void confirmPayment() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);

        assertNodeActive(wpi.getId(), kieSession, "Print Packing Slip");
        assertEquals("PAYMENT_CONFIRMED", wpi.getVariable("currentState"));
    }

    @Test
    public void confirmPayment_goBack() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, true);

        assertNodeActive(wpi.getId(), kieSession, "Validate Items In Stock");
        assertEquals("ACCEPTED_SHIPMENT", wpi.getVariable("currentState"));
    }

    @Test
    public void printPackingSlip() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, null);

        assertNodeActive(wpi.getId(), kieSession, "Prepare for Shipment");
        assertEquals("PRINTED_PACKING_SLIP", wpi.getVariable("currentState"));
    }

    @Test
    public void printPackingSlip_goBack() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, true);

        assertNodeActive(wpi.getId(), kieSession, "Wait for Payment Confirmation");
        assertEquals("INVENTORY_AVAILABLE", wpi.getVariable("currentState"));
    }

    @Test
    public void prepareForShipment() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, null);
        prepareForShipment(wpi, null);

        assertNodeActive(wpi.getId(), kieSession, "Out for Delivery");
        assertEquals("SHIPPED", wpi.getVariable("currentState"));
    }

    @Test
    public void prepareForShipment_goBack() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, null);
        prepareForShipment(wpi, true);

        assertNodeActive(wpi.getId(), kieSession, "Print Packing Slip");
        assertEquals("PAYMENT_CONFIRMED", wpi.getVariable("currentState"));
    }

    @Test
    public void deliver() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, null);
        prepareForShipment(wpi, null);
        deliver(wpi, null);

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("COMPLETED", wpi.getVariable("currentState"));
    }

    @Test
    public void deliver_goBack() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        validateStock(wpi, "IN_STOCK");
        confirmPayment(wpi, null);
        printPackingSlip(wpi, null);
        prepareForShipment(wpi, null);
        deliver(wpi, true);

        assertNodeActive(wpi.getId(), kieSession, "Prepare for Shipment");
        assertEquals("PRINTED_PACKING_SLIP", wpi.getVariable("currentState"));
    }

    @Test
    public void pick() {
        WorkflowProcessInstance wpi = createProcess();

        wpi.signalEvent("picked", null);

        assertNodeActive(wpi.getId(), kieSession, "Wait for Payment Confirmation");
        assertEquals("INVENTORY_AVAILABLE", wpi.getVariable("currentState"));
    }

    @Test
    public void fulfill() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        wpi.signalEvent("fulfilled", null);

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("COMPLETED", wpi.getVariable("currentState"));
    }

    @Test
    public void cancel() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        wpi.signalEvent("canceled", null);

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("CANCELED", wpi.getVariable("currentState"));
    }

    @Test
    public void customerCare() {
        WorkflowProcessInstance wpi = createProcess();

        acceptShipment(wpi, true);
        wpi.signalEvent("customer_care", null);

        assertProcessInstanceNotActive(wpi.getId(), kieSession);
        assertEquals("CUSTOMER_CARE", wpi.getVariable("currentState"));
    }

    private WorkflowProcessInstance createProcess() {
        Map<String, Object> processParam = new HashMap<>();
        processParam.put("initiator", "john");
        ProcessInstance processInstance = kieSession.startProcess("AFG_Custom_STH_Process", processParam);

        assertTrue(processInstance instanceof WorkflowProcessInstance);
        WorkflowProcessInstance wpi = (WorkflowProcessInstance) processInstance;

        assertNodeExists(wpi,
                "Accept Shipment",
                "Validate Items In Stock",
                "Wait for Payment Confirmation",
                "Print Packing Slip",
                "Prepare for Shipment",
                "Out for Delivery");

        assertNodeTriggered(wpi.getId(), "Pre-Accept Shipment", "Accept Shipment");
        assertEquals("PRE_ACCEPT_SHIPMENT", wpi.getVariable("currentState"));

        logger.info("Created process {}", wpi.getProcessName());

        return wpi;
    }

    private void acceptShipment(WorkflowProcessInstance wpi, Boolean shipmentAccepted) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Accept Shipment");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Accept Shipment", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("shipmentAccepted", shipmentAccepted);
        taskService.complete(task.getId(), "john", data);
    }

    private void validateStock(WorkflowProcessInstance wpi, String stockLevel) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Validate Items In Stock");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Validate Items In Stock", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("stockLevel", stockLevel);
        taskService.complete(task.getId(), "john", data);
    }

    private void confirmPayment(WorkflowProcessInstance wpi, Boolean back) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Wait for Payment Confirmation");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Wait for Payment Confirmation", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("back", back);
        taskService.complete(task.getId(), "john", data);
    }

    private void printPackingSlip(WorkflowProcessInstance wpi, Boolean back) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Print Packing Slip");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Print Packing Slip", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("back", back);
        taskService.complete(task.getId(), "john", data);
    }

    private void prepareForShipment(WorkflowProcessInstance wpi, Boolean back) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Prepare for Shipment");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Prepare for Shipment", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("back", back);
        taskService.complete(task.getId(), "john", data);
    }

    private void deliver(WorkflowProcessInstance wpi, Boolean back) {
        assertProcessInstanceActive(wpi.getId(), kieSession);
        assertNodeActive(wpi.getId(), kieSession, "Out for Delivery");

        List<TaskSummary> tasks = taskService.getTasksByStatusByProcessInstanceId(wpi.getId(), Arrays.asList(Reserved), "en-UK");
        assertEquals(1, tasks.size());
        TaskSummary task = tasks.get(0);
        assertEquals("Out for Delivery", task.getName());
        taskService.start(task.getId(), "john");
        Map<String, Object> data = new HashMap<>();
        data.put("back", back);
        taskService.complete(task.getId(), "john", data);
    }

}
