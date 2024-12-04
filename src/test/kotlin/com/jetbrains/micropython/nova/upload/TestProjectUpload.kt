package com.jetbrains.micropython.nova.upload

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.junit.jupiter.api.Test

@TestApplication
@TestFixtures
@TestDataPath("\$CONTENT_PATH/testData")
class TestProjectUpload() {

    private val project = projectFixture(openAfterCreation = true)
//    private val module = project.moduleFixture(pathFixture = )

    @Test
    fun testOpen() {
        println("project = ${project.get()}")
//        println("module = ${module}")
    }


}