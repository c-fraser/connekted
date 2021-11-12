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
package io.github.cfraser.connekted.cli

import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintCompletionMessage
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import javax.enterprise.inject.Instance
import javax.inject.Inject

/**
 *
 * The [CLIApplication] class is a [QuarkusApplication] which initializes and runs the *CLI*
 * application.
 *
 * The presence of the [QuarkusMain] annotations enables the generation of the `main` entry point at
 * build time.
 */
@QuarkusMain
internal class CLIApplication : QuarkusApplication {

  @Inject private lateinit var commands: Instance<CliktCommand>

  /**
   * Initialize then run *CLI* application.
   *
   * The [CliktCommand] subcommands are discovered via quarkus' dependency injection then registered
   * to the *root* [NoOpCliktCommand] which is executed with [args].
   *
   * @param args the application arguments to parse
   * @return the status code
   */
  override fun run(vararg args: String): Int {
    val rootCommand =
        NoOpCliktCommand(
                help = "Manage the `connekted` deployment and interact with messaging applications")
            .apply {
              context { helpFormatter = ColorfulHelpFormatter }
              subcommands(commands)
            }

    return try {
      rootCommand.parse(args.asList()).run { 0 }
    } catch (e: ProgramResult) {
      e.statusCode
    } catch (e: PrintHelpMessage) {
      TermUi.echo(e.command.getFormattedHelp())
      if (e.error) 1 else 0
    } catch (e: PrintCompletionMessage) {
      val s = if (e.forceUnixLineEndings) "\n" else rootCommand.currentContext.console.lineSeparator
      TermUi.echo(e.message, lineSeparator = s)
      0
    } catch (e: PrintMessage) {
      TermUi.echo(e.message)
      if (e.error) 1 else 0
    } catch (e: UsageError) {
      TermUi.echo(e.helpMessage(), err = true)
      e.statusCode
    } catch (e: CliktError) {
      TermUi.echo(e.message, err = true)
      1
    } catch (e: Abort) {
      TermUi.echo(rootCommand.currentContext.localization.aborted(), err = true)
      if (e.error) 1 else 0
    }
  }

  /**
   * [ColorfulHelpFormatter] is a [CliktHelpFormatter] implementation that uses [TextColors] and
   * [TextStyles] to render help messages.
   */
  private object ColorfulHelpFormatter : CliktHelpFormatter() {

    override fun renderTag(tag: String, value: String) =
        TextColors.green(super.renderTag(tag, value))

    override fun renderOptionName(name: String) = TextColors.yellow(super.renderOptionName(name))
    override fun renderArgumentName(name: String) =
        TextColors.yellow(super.renderArgumentName(name))

    override fun renderSubcommandName(name: String) =
        TextColors.yellow(super.renderSubcommandName(name))

    override fun renderSectionTitle(title: String) =
        (TextStyles.bold + TextStyles.underline)(super.renderSectionTitle(title))

    override fun optionMetavar(option: HelpFormatter.ParameterHelp.Option) =
        TextColors.green(super.optionMetavar(option))
  }
}
