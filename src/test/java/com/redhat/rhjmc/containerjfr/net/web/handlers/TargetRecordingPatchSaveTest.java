/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@ExtendWith(MockitoExtension.class)
class TargetRecordingPatchSaveTest {

    TargetRecordingPatchSave patchSave;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Clock clock;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;
    @Mock JFRConnection jfrConnection;
    @Mock IFlightRecorderService service;

    String targetId = "fooTarget";
    String recordingName = "someRecording";

    @BeforeEach
    void setup() {
        this.patchSave =
                new TargetRecordingPatchSave(fs, recordingsPath, targetConnectionManager, clock);
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
    }

    @Test
    void shouldThrow404IfNoMatchingRecordingFound() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(), Mockito.any(ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                ConnectedTask task = (ConnectedTask) invocation.getArgument(1);
                                return task.execute(jfrConnection);
                            }
                        });
        Mockito.when(jfrConnection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class,
                        () -> patchSave.handle(ctx, new ConnectionDescriptor(targetId)));

        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
    }

    @Test
    void shouldSaveRecording() throws Exception {
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(), Mockito.any(ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                ConnectedTask task = (ConnectedTask) invocation.getArgument(1);
                                return task.execute(jfrConnection);
                            }
                        });
        Mockito.when(jfrConnection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));
        Mockito.when(jfrConnection.getHost()).thenReturn("some-hostname.local");
        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        patchSave.handle(ctx, new ConnectionDescriptor(targetId));

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        inOrder.verify(resp).end("some-hostname-local_someRecording_" + timestamp + ".jfr");
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingThatEndsWithJfr() throws Exception {
        String recordingName = "someRecording.jfr";
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(), Mockito.any(ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                ConnectedTask task = (ConnectedTask) invocation.getArgument(1);
                                return task.execute(jfrConnection);
                            }
                        });
        Mockito.when(jfrConnection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));
        Mockito.when(jfrConnection.getHost()).thenReturn("some-hostname.local");
        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        patchSave.handle(ctx, new ConnectionDescriptor(targetId));

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        inOrder.verify(resp).end("some-hostname-local_someRecording_" + timestamp + ".jfr");
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }

    @Test
    void shouldSaveRecordingNumberedCopy() throws Exception {
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(), Mockito.any(ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                ConnectedTask task = (ConnectedTask) invocation.getArgument(1);
                                return task.execute(jfrConnection);
                            }
                        });
        Mockito.when(jfrConnection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));
        Mockito.when(jfrConnection.getHost()).thenReturn("some-hostname.local");
        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(recordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        patchSave.handle(ctx, new ConnectionDescriptor(targetId));

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(200);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        inOrder.verify(resp).end("some-hostname-local_someRecording_" + timestamp + ".1.jfr");
        Mockito.verify(fs).copy(Mockito.eq(stream), Mockito.eq(destination));
    }
}
