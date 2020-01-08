/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.exceptions.RegistrationException;
import io.aeron.logbuffer.LogBufferDescriptor;
import io.aeron.test.TestMediaDriver;
import org.agrona.ErrorHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class SpecifiedPositionPublicationTest
{
    @Test
    public void shouldRejectSpecifiedPositionForConcurrentPublications()
    {
        final ErrorHandler mockErrorHandler = mock(ErrorHandler.class);
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
            .errorHandler(mockErrorHandler)
            .dirDeleteOnShutdown(true)
            .publicationTermBufferLength(LogBufferDescriptor.TERM_MIN_LENGTH)
            .threadingMode(ThreadingMode.SHARED);

        try (TestMediaDriver ignore = TestMediaDriver.launch(mediaDriverContext);
            Aeron aeron = Aeron.connect())
        {
            final String channel = new ChannelUriStringBuilder()
                .media("ipc")
                .initialPosition(1024, -873648623, 65536)
                .build();

            assertThrows(RegistrationException.class, () -> aeron.addPublication(channel, 1001));
        }
    }
}
