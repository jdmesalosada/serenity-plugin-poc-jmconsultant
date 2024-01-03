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

import java.time.Instant

class SendResultsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def extension = project.extensions.create("influxDbConf", SendResultsExtension)

        project.task('sendResults') {
            doLast {

                Path outputDir = Paths.get(project.serenity.outputDirectory)
                if (!outputDir.isAbsolute()) {
                    outputDir = project.projectDir.toPath().resolve(outputDir)
                }

                print "output Dir: ${outputDir}"

                OutcomeFormat format = OutcomeFormat.JSON

                File file = new File(outputDir.toString())

                TestOutcomes outcomes = TestOutcomeLoader
                        .loadTestOutcomes().inFormat(format).from(file)

                println "scenarios count ${outcomes.scenarioCount}"

                def database = extension.database
                def measurement = extension.measurement
                def suite = extension.suite
                def host = extension.host
                def username = extension.username
                def pass = extension.pass

                logger.lifecycle("database: ${database}")
                logger.lifecycle("measurement: ${measurement}")
                logger.lifecycle("host: ${host}")
                logger.lifecycle("suite: ${suite}")

                def executionId = UUID.randomUUID().toString()

                InfluxDBClient influxDBClient = InfluxDBClientFactory
                        .createV1(host, username, pass.toCharArray(), database, null)


                WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking()

                List<Point> results = new ArrayList<>()

                outcomes.getOutcomes().each {
                    println "********"
                    println "complete name: ${it.getCompleteName()}"
                    println "execution id: ${executionId}"
                    println "duration: ${it.getDuration()}"
                    println "********"

                    Point result = Point
                            .measurement(measurement)
                            .time(Instant.now(), WritePrecision.MS)
                            .addTag("testName", it.getCompleteName())
                            .addTag("result", it.getResult().toString())
                            .addTag("executionId", executionId)
                            .addTag("suite", suite)
                            .addField("testDuration", it.getDuration())

                    results.add(result)

                }

                try{
                    writeApi.writePoints(results)
                }
                catch (Exception ex){
                    influxDBClient.close()
                    logger.error("unable to write the results in the influx db due to: ${ex.message}")
                }

            }
        }

    }

}