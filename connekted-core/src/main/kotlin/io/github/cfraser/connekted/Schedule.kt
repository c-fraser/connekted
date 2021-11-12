/*
Copyright 2021 c-fraser

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.github.cfraser.connekted

import com.cronutils.descriptor.CronDescriptor
import com.cronutils.model.Cron
import com.cronutils.model.time.ExecutionTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * A [Schedule] represents a type that can calculate the next execution time based on the current
 * time and previous execution time.
 */
interface Schedule {

  /**
   * Return the next execution time as an [Instant].
   *
   * @param currentTime the current time
   * @param previousExecutionTime the previous execution time, if this value is `null` then the next
   * execution will be the first
   * @return the next execution time
   */
  fun nextExecutionTime(currentTime: Instant, previousExecutionTime: Instant?): Instant
}

/**
 * A [CronSchedule] is a [Schedule] implementation that uses a [com.cronutils.model.Cron] expression
 * to determine the next execution time.
 *
 * @property zoneId the [ZoneId] to use to convert the current time to a [ZonedDateTime]
 * @constructor use the given [Cron] to initialize an [ExecutionTime] instance
 * @param cron the [Cron] instance which represents the execution schedule
 */
class CronSchedule(cron: Cron, private val zoneId: ZoneId = ZoneId.systemDefault()) : Schedule {

  private val executionTime = ExecutionTime.forCron(cron)
  private val description = CronDescriptor.instance(Locale.ENGLISH).describe(cron)

  override fun nextExecutionTime(currentTime: Instant, previousExecutionTime: Instant?): Instant {
    val zonedDateTime = ZonedDateTime.ofInstant(currentTime, zoneId)
    return executionTime
        .nextExecution(zonedDateTime)
        .map { nextExecution -> nextExecution.toInstant() }
        .orElseThrow {
          IllegalStateException(
              "failed to calculate next execution time from $zonedDateTime with $executionTime")
        }
  }

  override fun toString(): String = "${Cron::class.simpleName}($description)"
}

/**
 * A [FixedIntervalSchedule] is a [Schedule] implementation that executes on a fixed interval.
 *
 * @property interval the [Duration] which represents the execution interval
 */
class FixedIntervalSchedule(private val interval: Duration) : Schedule {

  override fun nextExecutionTime(currentTime: Instant, previousExecutionTime: Instant?): Instant =
      (previousExecutionTime ?: currentTime).plus(interval)

  override fun toString(): String = "${FixedIntervalSchedule::class.simpleName}($interval)"
}

/**
 * An [InitialDelaySchedule] is a [Schedule] which executes after the [initialDelay] for the first
 * execution, subsequent execution times will be determined by the provided [schedule].
 *
 * @property initialDelay the amount of time to wait before the first execution
 * @property schedule the schedule which determines subsequent execution times
 */
class InitialDelaySchedule(private val initialDelay: Duration, private val schedule: Schedule) :
    Schedule {

  override fun nextExecutionTime(currentTime: Instant, previousExecutionTime: Instant?): Instant {
    return if (previousExecutionTime == null) currentTime.plus(initialDelay)
    else schedule.nextExecutionTime(currentTime, previousExecutionTime)
  }

  override fun toString(): String =
      "${InitialDelaySchedule::class.simpleName}($initialDelay, $schedule)"
}
