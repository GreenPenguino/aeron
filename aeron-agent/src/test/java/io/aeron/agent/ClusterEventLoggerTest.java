/*
 * Copyright 2014-2022 Real Logic Limited.
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
package io.aeron.agent;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static io.aeron.agent.AgentTests.verifyLogHeader;
import static io.aeron.agent.ClusterEventCode.*;
import static io.aeron.agent.ClusterEventEncoder.electionStateChangeLength;
import static io.aeron.agent.ClusterEventEncoder.newLeaderShipTermLength;
import static io.aeron.agent.CommonEventEncoder.LOG_HEADER_LENGTH;
import static io.aeron.agent.CommonEventEncoder.STATE_SEPARATOR;
import static io.aeron.agent.EventConfiguration.BUFFER_LENGTH_DEFAULT;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.agrona.BitUtil.*;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.ALIGNMENT;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.encodedMsgOffset;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TAIL_POSITION_OFFSET;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClusterEventLoggerTest
{
    private static final int CAPACITY = align(BUFFER_LENGTH_DEFAULT, CACHE_LINE_LENGTH);
    private final UnsafeBuffer logBuffer = new UnsafeBuffer(
        allocateDirectAligned(BUFFER_LENGTH_DEFAULT + TRAILER_LENGTH, CACHE_LINE_LENGTH));
    private final ClusterEventLogger logger = new ClusterEventLogger(new ManyToOneRingBuffer(logBuffer));

    @Test
    void logNewLeadershipTerm()
    {
        final int offset = align(22, ALIGNMENT);
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final long logLeadershipTermId = 434;
        final long nextLeadershipTermId = 2561;
        final long nextTermBaseLogPosition = 2562;
        final long nextLogPosition = 2563;
        final long leadershipTermId = -500;
        final long logPosition = 43;
        final long timestamp = 2;
        final int leaderMemberId = 0;
        final int logSessionId = 3;
        final int captureLength = newLeaderShipTermLength();
        final boolean isStartup = true;
        final long termBaseLogPosition = 982734;
        final long leaderRecordingId = 76434;

        logger.logNewLeadershipTerm(
            logLeadershipTermId,
            nextLeadershipTermId,
            nextTermBaseLogPosition,
            nextLogPosition,
            leadershipTermId,
            termBaseLogPosition,
            logPosition,
            leaderRecordingId,
            timestamp,
            leaderMemberId,
            logSessionId,
            isStartup);

        verifyLogHeader(logBuffer, offset, NEW_LEADERSHIP_TERM.toEventCodeId(), captureLength, captureLength);
        int index = encodedMsgOffset(offset) + LOG_HEADER_LENGTH;
        assertEquals(logLeadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(nextLeadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(
            nextTermBaseLogPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(nextLogPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(leadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(termBaseLogPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(logPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(leaderRecordingId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(timestamp, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(leaderMemberId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;
        assertEquals(logSessionId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;
        assertEquals(isStartup, 1 == logBuffer.getInt(index, LITTLE_ENDIAN));
    }

    @Test
    void logStateChange()
    {
        final int offset = ALIGNMENT * 11;
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final TimeUnit from = MINUTES;
        final TimeUnit to = SECONDS;
        final int memberId = 42;
        final String payload = from.name() + STATE_SEPARATOR + to.name();
        final int captureLength = SIZE_OF_INT * 2 + payload.length();

        logger.logStateChange(STATE_CHANGE, from, to, memberId);

        verifyLogHeader(logBuffer, offset, STATE_CHANGE.toEventCodeId(), captureLength, captureLength);
        final int index = encodedMsgOffset(offset) + LOG_HEADER_LENGTH;
        assertEquals(memberId, logBuffer.getInt(index, LITTLE_ENDIAN));
        assertEquals(payload, logBuffer.getStringAscii(index + SIZE_OF_INT));
    }

    @Test
    void logElectionStateChange()
    {
        final int offset = ALIGNMENT * 4;
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final ChronoUnit from = ChronoUnit.ERAS;
        final ChronoUnit to = null;
        final int memberId = 18;
        final int leaderId = -1;
        final long candidateTermId = 29L;
        final long leadershipTermId = 0L;
        final long logPosition = 100L;
        final long logLeadershipTermId = -9L;
        final long appendPosition = 16 * 1024L;
        final long catchupPosition = 8192L;
        final int length = electionStateChangeLength(from, to);

        logger.logElectionStateChange(
            from,
            to,
            memberId,
            leaderId,
            candidateTermId,
            leadershipTermId,
            logPosition,
            logLeadershipTermId,
            appendPosition,
            catchupPosition);

        verifyLogHeader(logBuffer, offset, ELECTION_STATE_CHANGE.toEventCodeId(), length, length);
        int index = encodedMsgOffset(offset) + LOG_HEADER_LENGTH;
        assertEquals(memberId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;
        assertEquals(leaderId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;
        assertEquals(candidateTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(leadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(logPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(logLeadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(appendPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(catchupPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(from.name() + STATE_SEPARATOR + "null", logBuffer.getStringAscii(index));
    }

    @Test
    void logCatchupPosition()
    {
        final int offset = ALIGNMENT * 4;
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final long leadershipTermId = 1233L;
        final long logPosition = 100L;
        final int followerMemberId = 18;
        final String catchupEndpoint = "aeron:udp?endpoint=localhost:9090";

        logger.logCatchupPosition(leadershipTermId, logPosition, followerMemberId, catchupEndpoint);

        final int length = (2 * SIZE_OF_LONG) + SIZE_OF_INT + SIZE_OF_INT + catchupEndpoint.length();
        verifyLogHeader(logBuffer, offset, CATCHUP_POSITION.toEventCodeId(), length, length);
        int index = encodedMsgOffset(offset) + LOG_HEADER_LENGTH;
        assertEquals(leadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(logPosition, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(followerMemberId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;
        final int catchupEndpointLength = logBuffer.getInt(index, LITTLE_ENDIAN);
        index += SIZE_OF_INT;
        assertEquals(catchupEndpoint, logBuffer.getStringWithoutLengthAscii(index, catchupEndpointLength));

        final StringBuilder sb = new StringBuilder();
        ClusterEventDissector.dissectCatchupPosition(CATCHUP_POSITION, logBuffer, encodedMsgOffset(offset), sb);

        final String expectedMessagePattern = "\\[[0-9]+\\.[0-9]+\\] CLUSTER: CATCHUP_POSITION \\[57/57\\]: " +
            "leadershipTermId=1233 logPosition=100 followerMemberId=18 " +
            "catchupEndpoint=aeron:udp\\?endpoint=localhost:9090";

        assertThat(sb.toString(), Matchers.matchesPattern(expectedMessagePattern));
    }

    @Test
    void logCatchupPositionLongCatchupEndpoint()
    {
        final int offset = ALIGNMENT * 4;
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final long leadershipTermId = 1233L;
        final long logPosition = 100L;
        final int followerMemberId = 18;

        final byte[] alias = new byte[8192];
        Arrays.fill(alias, (byte)'x');

        final String catchupEndpoint = "aeron:udp?endpoint=localhost:9090|alias=" + new String(
            alias,
            StandardCharsets.US_ASCII);

        logger.logCatchupPosition(leadershipTermId, logPosition, followerMemberId, catchupEndpoint);

        final StringBuilder sb = new StringBuilder();
        ClusterEventDissector.dissectCatchupPosition(CATCHUP_POSITION, logBuffer, encodedMsgOffset(offset), sb);

        final String expectedMessagePattern = "\\[[0-9]*\\.[0-9]*\\] CLUSTER: CATCHUP_POSITION \\[[0-9]*/8256\\]: " +
            "leadershipTermId=1233 logPosition=100 followerMemberId=18 " +
            "catchupEndpoint=aeron:udp\\?endpoint=localhost:9090\\|alias" +
            "=(x)*\\.\\.\\.";

        assertThat(sb.toString(), Matchers.matchesPattern(expectedMessagePattern));
    }

    @Test
    void logStopCatchup()
    {
        final int offset = ALIGNMENT * 4;
        logBuffer.putLong(CAPACITY + TAIL_POSITION_OFFSET, offset);
        final long leadershipTermId = 1233L;
        final int followerMemberId = 18;

        logger.logStopCatchup(leadershipTermId, followerMemberId);

        final int length = SIZE_OF_LONG + SIZE_OF_INT;
        verifyLogHeader(logBuffer, offset, STOP_CATCHUP.toEventCodeId(), length, length);
        int index = encodedMsgOffset(offset) + LOG_HEADER_LENGTH;
        assertEquals(leadershipTermId, logBuffer.getLong(index, LITTLE_ENDIAN));
        index += SIZE_OF_LONG;
        assertEquals(followerMemberId, logBuffer.getInt(index, LITTLE_ENDIAN));
        index += SIZE_OF_INT;

        final StringBuilder sb = new StringBuilder();
        ClusterEventDissector.dissectStopCatchup(STOP_CATCHUP, logBuffer, encodedMsgOffset(offset), sb);

        final String expectedMessagePattern = "\\[[0-9]+\\.[0-9]+\\] CLUSTER: STOP_CATCHUP \\[12/12\\]: " +
            "leadershipTermId=1233 followerMemberId=18";

        assertThat(sb.toString(), Matchers.matchesPattern(expectedMessagePattern));
    }
}
