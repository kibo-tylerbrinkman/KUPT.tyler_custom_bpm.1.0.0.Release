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

class Fulfillment_Curbside_Process_Test : JbpmJUnitBaseTestCase(true, false) {
    private var kieSession: KieSession? = null
    private var taskService: TaskService? = null

    @Before
    fun init() {
        createRuntimeManager("com/kibocommerce/bpm/fulfillment/FulfillmentProcess-Curbside.bpmn")
        val runtimeEngine = getRuntimeEngine(null)
        kieSession = runtimeEngine.kieSession
        taskService = runtimeEngine.taskService
    }

    @Test
    fun validateStock_inStock() {
        val wpi = createProcess()

        validateStock(wpi, "IN_STOCK")

        assertNodeActive(wpi.id, kieSession, "Provide to Customer")
        assertCurrentState(wpi, "READY_FOR_PICKUP")
    }

    @Test
    fun validateStock_partialStock() {
        val wpi = createProcess()

        validateStock(wpi, "PARTIAL_STOCK")

        assertNodeActive(wpi.id, kieSession, "Provide to Customer")
        assertCurrentState(wpi, "PARTIAL_INVENTORY_NOPE")
    }

    @Test
    fun validateStock_noStock() {
        val wpi = createProcess()

        validateStock(wpi, "NO_STOCK")

        assertNodeActive(wpi.id, kieSession, "rejected")
        assertCurrentState(wpi, "INVENTORY_NOPE")
    }

    @Test
    fun provideToCustomer() {
        val wpi = createProcess()

        validateStock(wpi, "IN_STOCK")
        provideToCustomer(wpi)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun rejected() {
        val wpi = createProcess()

        wpi.signalEvent("rejected", null)

        assertCurrentState(wpi, "REJECTED")
    }

    @Test
    fun fulfill() {
        val wpi = createProcess()

        wpi.signalEvent("fulfilled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun cancel() {
        val wpi = createProcess()

        validateStock(wpi, "NO_STOCK")
        wpi.signalEvent("canceled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CANCELED")
    }

    @Test
    fun customerCare() {
        val wpi = createProcess()

        validateStock(wpi, "NO_STOCK")
        wpi.signalEvent("customer_care", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CUSTOMER_CARE")
    }

    private fun createProcess(): WorkflowProcessInstance {
        val processParam = mapOf("initiator" to "john")
        val processInstance = kieSession!!.startProcess("fulfillment.FulfillmentProcess-Curbside", processParam)

        assertTrue(processInstance is WorkflowProcessInstance)
        assertNodeExists(
                processInstance,
                "Validate Stock",
                "Provide to Customer"
        )
        assertNodeTriggered(processInstance.id, "Start", "Validate Stock")
        assertEquals("STARTED", processInstance.getVariable("currentState"))

        logger.info("Created process {}", processInstance.processName)
        return processInstance
    }

    private fun validateStock(wpi: WorkflowProcessInstance, stockLevel: String) {
        val expectedTaskName = "Validate Stock"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        val data = mapOf("stockLevel" to stockLevel)
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
        private val logger = LoggerFactory.getLogger(Fulfillment_Curbside_Process_Test::class.java)
    }
}