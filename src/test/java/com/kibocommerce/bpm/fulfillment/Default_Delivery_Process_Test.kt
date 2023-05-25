package com.kibocommerce.bpm.fulfillment

import org.jbpm.test.JbpmJUnitBaseTestCase
import org.junit.Before
import org.junit.Test
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.process.WorkflowProcessInstance
import org.kie.api.task.TaskService
import org.kie.api.task.model.Status
import org.slf4j.LoggerFactory
import kotlin.collections.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Default_Delivery_Process_Test : JbpmJUnitBaseTestCase(true, false) {
    private var kieSession: KieSession? = null
    private var taskService: TaskService? = null

    @Before
    fun init() {
        createRuntimeManager("com/kibocommerce/bpm/fulfillment/Default_Delivery_Process.bpmn")
        val runtimeEngine = getRuntimeEngine(null)
        kieSession = runtimeEngine.kieSession
        taskService = runtimeEngine.taskService
    }

    @Test
    fun acceptShipment_true() {
        val wpi = createProcess()

        acceptShipment(wpi, true)

        assertNodeActive(wpi.id, kieSession, "Print Pick List")
        assertCurrentState(wpi, "ACCEPTED_SHIPMENT")
    }

    @Test
    fun acceptShipment_false() {
        val wpi = createProcess()

        acceptShipment(wpi, false)

        assertProcessInstanceActive(wpi.id, kieSession)
        assertCurrentState(wpi, "REJECTED")
    }

    @Test
    fun printPickList() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)

        assertNodeActive(wpi.id, kieSession, "Validate Items In Stock")
        assertCurrentState(wpi, "PROCESSING_PICK_LIST")
    }

    @Test
    fun validateStock_inStock() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "IN_STOCK", null)

        assertNodeActive(wpi.id, kieSession, "Delivery Preparation")
        assertCurrentState(wpi, "INVENTORY_AVAILABLE")
    }

    @Test
    fun validateStock_partialStock_noTransfer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "PARTIAL_STOCK", false)

        assertNodeActive(wpi.id, kieSession, "Delivery Preparation")
        assertCurrentState(wpi, "PARTIAL_INVENTORY_NOPE")
    }

    @Test
    fun validateStock_partialStock_createTransfer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "PARTIAL_STOCK", true)

        assertNodeActive(wpi.id, kieSession, "Wait for Transfer")
        assertCurrentState(wpi, "WAITING_FOR_TRANSFER")
    }

    @Test
    fun validateStock_noStock_noTransfer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "NO_STOCK", false)

        assertNodeActive(wpi.id, kieSession, "rejected")
        assertCurrentState(wpi, "INVENTORY_NOPE")
    }

    @Test
    fun validateStock_noStock_createTransfer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "NO_STOCK", true)

        assertNodeActive(wpi.id, kieSession, "Wait for Transfer")
        assertCurrentState(wpi, "WAITING_FOR_TRANSFER")
    }

    @Test
    fun waitForTransfer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "NO_STOCK", true)
        waitForTransfer(wpi)

        assertNodeActive(wpi.id, kieSession, "Delivery Preparation")
        assertCurrentState(wpi, "ALL_TRANSFERS_RECEIVED")
    }

    @Test
    fun deliveryPreparation() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "IN_STOCK", false)
        deliveryPreparation(wpi, true)

        assertNodeActive(wpi.id, kieSession, "Provide to Customer")
        assertCurrentState(wpi, "READY_FOR_DELIVERY")
    }

    @Test
    fun provideToCustomer() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        printPickList(wpi)
        validateStock(wpi, "IN_STOCK", null)
        deliveryPreparation(wpi, true)
        provideToCustomer(wpi)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun pick() {
        val wpi = createProcess()

        wpi.signalEvent("picked", null)

        assertNodeActive(wpi.id, kieSession, "Provide to Customer")
        assertCurrentState(wpi, "READY_FOR_DELIVERY")
    }

    @Test
    fun fulfill() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        wpi.signalEvent("fulfilled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun cancel() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        wpi.signalEvent("canceled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CANCELED")
    }

    @Test
    fun customerCare() {
        val wpi = createProcess()

        acceptShipment(wpi, true)
        wpi.signalEvent("customer_care", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CUSTOMER_CARE")
    }

    private fun createProcess(): WorkflowProcessInstance {
        val processParam = mapOf("initiator" to "john")
        val processInstance = kieSession!!.startProcess("Default_Delivery_Process", processParam)

        assertTrue(processInstance is WorkflowProcessInstance)
        assertNodeExists(
                processInstance,
                "Accept Shipment",
                "Print Pick List",
                "Validate Items In Stock",
                "Wait for Transfer",
                "Delivery Preparation",
                "Provide to Customer"
        )
        assertNodeTriggered(processInstance.id, "Pre-Accept Shipment", "Accept Shipment")
        assertEquals("PRE_ACCEPT_SHIPMENT", processInstance.getVariable("currentState"))

        logger.info("Created process {}", processInstance.processName)
        return processInstance
    }

    private fun acceptShipment(wpi: WorkflowProcessInstance, shipmentAccepted: Boolean) {
        val expectedTaskName = "Accept Shipment"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        val data = mapOf("shipmentAccepted" to shipmentAccepted)
        taskService!!.complete(task.id, "john", data)
    }

    private fun printPickList(wpi: WorkflowProcessInstance) {
        val expectedTaskName = "Print Pick List"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        taskService!!.complete(task.id, "john", HashMap())
    }

    private fun validateStock(wpi: WorkflowProcessInstance, stockLevel: String, createTransfer: Boolean?) {
        val expectedTaskName = "Validate Items In Stock"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        val data = mapOf(
                "stockLevel" to stockLevel,
                "createTransfer" to createTransfer
        )
        taskService!!.complete(task.id, "john", data)
    }

    private fun waitForTransfer(wpi: WorkflowProcessInstance) {
        val expectedTaskName = "Wait for Transfer"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        taskService!!.complete(task.id, "john", HashMap())
    }

    private fun deliveryPreparation(wpi: WorkflowProcessInstance, assemblyCompleted: Boolean?) {
        val expectedTaskName = "Delivery Preparation"
        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        val data = mapOf("assemblyCompleted" to assemblyCompleted)
        taskService!!.complete(task.id, "john", data)
    }

    private fun provideToCustomer(wpi: WorkflowProcessInstance) {
        val expectedTaskName = "Provide to Customer"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        taskService!!.complete(task.id, "john", HashMap())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Default_Delivery_Process_Test::class.java)
    }
}