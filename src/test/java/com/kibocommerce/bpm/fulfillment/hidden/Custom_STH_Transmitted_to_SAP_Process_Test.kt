package com.kibocommerce.bpm.fulfillment.hidden

import com.kibocommerce.bpm.fulfillment.assertCurrentState
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

class Custom_STH_Transmitted_to_SAP_Process_Test : JbpmJUnitBaseTestCase(true, false) {
    private var kieSession: KieSession? = null
    private var taskService: TaskService? = null

    @Before
    fun init() {
        createRuntimeManager("com/kibocommerce/bpm/fulfillment/hidden/Custom_STH_Transmitted_to_SAP.bpmn")
        val runtimeEngine = getRuntimeEngine(null)
        kieSession = runtimeEngine.kieSession
        taskService = runtimeEngine.taskService
    }

    @Test
    fun transmitToSAP_splitShipment() {
        val wpi = createProcess()

        transmitToSAP(wpi, true)

        assertNodeActive(wpi.id, kieSession, "Ship")
        assertCurrentState(wpi, "TRANSMITTED_TO_SAP")
    }

    @Test
    fun transmitToSAP_no_splitShipment() {
        val wpi = createProcess()

        transmitToSAP(wpi, false)

        assertNodeActive(wpi.id, kieSession, "Ship")
        assertCurrentState(wpi, "TRANSMITTED_TO_SAP")
    }

    @Test
    fun ship() {
        val wpi = createProcess()

        transmitToSAP(wpi, false)
        ship(wpi)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun rejected() {
        val wpi = createProcess()

        transmitToSAP(wpi, false)
        wpi.signalEvent("rejected", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "REASSIGN_SHIPMENT")
    }

    @Test
    fun cancel() {
        val wpi = createProcess()

        transmitToSAP(wpi, false)
        wpi.signalEvent("canceled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CANCELED")
    }

    @Test
    fun customerCare() {
        val wpi = createProcess()

        transmitToSAP(wpi, false)
        wpi.signalEvent("customer_care", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CUSTOMER_CARE")
    }

    private fun createProcess(): WorkflowProcessInstance {
        val processParam = mapOf("initiator" to "john")
        val processInstance = kieSession!!.startProcess("Custom_STH_Transmitted_to_SAP", processParam)

        assertTrue(processInstance is WorkflowProcessInstance)
        assertNodeExists(
                processInstance,
                "Transmit to SAP",
                "Ship"
        )
        assertNodeTriggered(processInstance.id, null, "Transmit to SAP")
        assertEquals("NEW_ORDER", processInstance.getVariable("currentState"))

        logger.info("Created process {}", processInstance.processName)
        return processInstance
    }

    private fun transmitToSAP(wpi: WorkflowProcessInstance, splitShipment: Boolean?) {
        val expectedTaskName = "Transmit to SAP"

        assertProcessInstanceActive(wpi.id, kieSession)
        assertNodeActive(wpi.id, kieSession, expectedTaskName)

        val tasks = taskService!!.getTasksByStatusByProcessInstanceId(wpi.id, listOf(Status.Reserved), "en-UK")
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals(expectedTaskName, task.name)

        taskService!!.start(task.id, "john")
        val data = mapOf("splitShipment" to splitShipment)
        taskService!!.complete(task.id, "john", data)
    }

    private fun ship(wpi: WorkflowProcessInstance) {
        val expectedTaskName = "Ship"

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
        private val logger = LoggerFactory.getLogger(Custom_STH_Transmitted_to_SAP_Process_Test::class.java)
    }
}