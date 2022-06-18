package com.jmconsultant.plugins.serenityinfluxdb

import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.WriteApiBlocking
import com.influxdb.client.domain.WritePrecision
import net.thucydides.core.reports.OutcomeFormat
import net.thucydides.core.reports.TestOutcomes
import net.thucydides.core.reports.TestOutcomeLoader
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Path
import java.nio.file.Paths

import com.influxdb.client.write.Point

class SendResultsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        project.task('julianas') {
            doLast {
                def database = "serenityResults"
                //Influx 2.0
                /*InfluxDBClient influxDBClient = InfluxDBClientFactory
                        .create("http://localhost:8086", token, org, bucket)*/

                InfluxDBClient influxDBClient = InfluxDBClientFactory
                        .createV1("http://localhost:8086", "root", "root".toCharArray(), database, null)


                Path outputDir = Paths.get(project.serenity.outputDirectory)
                if (!outputDir.isAbsolute()) {
                    outputDir = project.projectDir.toPath().resolve(outputDir)
                }
                println "output dir " + outputDir

                OutcomeFormat format = OutcomeFormat.JSON
                File file = new File(outputDir.toString())

                TestOutcomes outcomes = TestOutcomeLoader.loadTestOutcomes().inFormat(format).from(file)
                println "Scenarios count: -> " + outcomes.scenarioCount

                WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking()

                def executionId = UUID.randomUUID().toString()
                List<Point> results = new ArrayList<>()

                outcomes.getOutcomes().each {
                    println "*********************"
                    println "complete name: " + it.getCompleteName()
                    println "test case name: " + it.getTestCaseName()
                    println "result: " + it.getResult().toString()
                    println "execution id: " + executionId
                    println "*********************"

                    Point point = Point.measurement("testResult")
                            .time(System.currentTimeMillis(), WritePrecision.NS)
                            .addTag("executionId", executionId)
                            .addTag("name", it.getCompleteName())
                            .addTag("result", it.getResult().toString())
                            .addField("duration", it.getDuration())
                            /*.addTag("executionId", executionId)
                            .addTag("name", it.getCompleteName())
                            .addTag("result", it.getResult().toString())
                            .addField("result", it.getResult().toString())
                            .addField("duration", it.getDuration())*/

                    results.add(point)
                }

                writeApi.writePoints(results)
                influxDBClient.close()
            }
        }
    }

}