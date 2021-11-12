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
package io.github.cfraser.connekted.e2e.test;

import io.github.cfraser.connekted.Connekted;
import io.github.cfraser.connekted.FixedIntervalSchedule;
import io.github.cfraser.connekted.e2e.test.connekt.TransportKt;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;

public final class Example06 {

  public static void main() {
    Connekted.initialize(
            TransportKt.getTransport(),
            messagingApplicationBuilder -> {
              messagingApplicationBuilder.<String>addSender(
                  senderBuilder -> {
                    senderBuilder.setName("random-strings-sender");
                    senderBuilder.setSendTo("random-strings");
                    senderBuilder.setSchedule(new FixedIntervalSchedule(Duration.ofSeconds(30)));
                    senderBuilder.send(
                        () ->
                            Flowable.create(
                                emitter -> {
                                  for (int i = 0; i < 5; i++) {
                                    var randomString = RandomStringUtils.randomAlphabetic(5);
                                    senderBuilder.getLogger().info("sending {}", randomString);
                                    emitter.onNext(randomString);
                                  }
                                  emitter.onComplete();
                                },
                                BackpressureStrategy.BUFFER));
                    senderBuilder.serialize(String::getBytes);
                  });

              messagingApplicationBuilder.<String>addReceiver(
                  receiverBuilder -> {
                    receiverBuilder.setName("reversed-random-strings-receiver");
                    receiverBuilder.setReceiveFrom("reversed-random-strings");
                    receiverBuilder.onMessage(
                        s -> receiverBuilder.getLogger().info("received {}", s));
                    receiverBuilder.deserialize(String::new);
                  });
            })
        .run();
  }
}
