/*
 * Copyright (C) 2016 VSCT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.vsct.dt.maze.topology

import com.github.dockerjava.api.model.PortBinding
import fr.vsct.dt.maze.TechnicalTest
import fr.vsct.dt.maze.core.Commands._
import fr.vsct.dt.maze.core.Predef._
import fr.vsct.dt.maze.helpers.Http

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

class DockerTest extends TechnicalTest {

  ignore should "do what it's supposed to do" in {

    println("'toto' exists? : " + Docker.client.inspectContainerCmd("toto").exec().getState)

    // Stopping all previously launched es containers to avoid collisions
    Docker.listContainers().foreach { container =>
      println(s"Found container: ${container.getId} with names ${container.getNames.mkString(",")}")
      if (container.getNames.exists(_.contains("elasticsearch"))) {
        Docker.stopContainer(container.getId)
        Docker.forceRemoveContainer(container.getId)
      }

    }

    val containerSpec = Docker.prepareCreateContainer("elasticsearch:1.7.3")
      .withPortBindings(
        PortBinding.parse("8300:9300"),
        PortBinding.parse("8200:9200")
      ).withName("elasticsearch")


    val containerId = Docker.createAndStartContainer(containerSpec)

    println("Waiting for container to be in running state")

    waitUntil(Docker.client.inspectContainerCmd(containerId).exec().getState.getRunning is true)

    println(s"$containerId exists? : ${Docker.client.inspectContainerCmd(containerId).exec().getState}")

    println(s"created container with id $containerId")

    println("Waiting for ES..")

    waitUntil(Http.get("http://localhost:8200").status is 200)
    println("ES is ready.")

    Docker.executionOnContainer(containerId, "curl", "-XPOST", "http://localhost:9200/_cluster/nodes/_local/_shutdown")
    waitUntil(Http.get("http://localhost:8200").isError)
    waitFor(5 seconds)

    Docker.listContainers().foreach { container =>
      if (container.getNames.exists(_.contains("elasticsearch"))) {
        println(s"Found ES container: ${container.getId} with names ${container.getNames.mkString(",")}")
        println("Container was not stopped properly, stopping it now")
        Docker.stopContainer(container.getId)
      }

    }
    Docker.forceRemoveContainer(containerId)
    println(s"Stopped container $containerId")

  }
}