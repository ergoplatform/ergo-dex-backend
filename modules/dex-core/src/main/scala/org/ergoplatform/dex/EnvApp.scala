package org.ergoplatform.dex

import fs2.Stream
import monix.eval.{Task, TaskApp}
import tofu.env.Env
import tofu.logging.{Loggable, LoggableContext, Logs}

abstract class EnvApp[C: Loggable] extends TaskApp {

  type InitF[+A]   = Task[A]
  type RunF[+A]    = Env[C, A]
  type StreamF[+A] = Stream[RunF, A]

  implicit def logs: Logs[InitF, RunF]                = Logs.withContext[InitF, RunF]
  implicit def loggableContext: LoggableContext[RunF] = LoggableContext.of[RunF].instance[C]
}
