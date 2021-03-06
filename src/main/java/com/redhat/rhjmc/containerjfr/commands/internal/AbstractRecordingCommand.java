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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.templates.Template;
import com.redhat.rhjmc.containerjfr.core.templates.TemplateType;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;

public abstract class AbstractRecordingCommand extends AbstractConnectedCommand {

    // TODO extract this somewhere more appropriate
    public static final Template ALL_EVENTS_TEMPLATE =
            new Template(
                    "ALL",
                    "Enable all available events in the target JVM, with default option values. This will be very expensive and is intended primarily for testing ContainerJFR's own capabilities.",
                    "ContainerJFR",
                    TemplateType.TARGET);

    private static final Pattern TEMPLATE_PATTERN =
            Pattern.compile("^template=([\\w]+)(?:,type=([\\w]+))?$");
    private static final Pattern EVENTS_PATTERN =
            Pattern.compile("([\\w\\.\\$]+):([\\w]+)=([\\w\\d\\.]+)");

    protected final ClientWriter cw;
    protected final EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    protected final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    protected AbstractRecordingCommand(
            ClientWriter cw,
            TargetConnectionManager targetConnectionManager,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
        super(targetConnectionManager);
        this.cw = cw;
        this.eventOptionsBuilderFactory = eventOptionsBuilderFactory;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
    }

    protected IConstrainedMap<EventOptionID> enableEvents(JFRConnection connection, String events)
            throws Exception {
        if (TEMPLATE_PATTERN.matcher(events).matches()) {
            Matcher m = TEMPLATE_PATTERN.matcher(events);
            m.find();
            String templateName = m.group(1);
            String typeName = m.group(2);
            if (ALL_EVENTS_TEMPLATE.getName().equals(templateName)) {
                return enableAllEvents(connection);
            }
            if (typeName != null) {
                return connection
                        .getTemplateService()
                        .getEvents(templateName, TemplateType.valueOf(typeName))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                String.format(
                                                        "No template \"%s\" found with type %s",
                                                        templateName, typeName)));
            }
            // if template type not specified, try to find a Custom template by that name. If none,
            // fall back on finding a Target built-in template by the name. If not, throw an
            // exception and bail out.
            return connection
                    .getTemplateService()
                    .getEvents(templateName, TemplateType.CUSTOM)
                    .or(
                            () -> {
                                try {
                                    return connection
                                            .getTemplateService()
                                            .getEvents(templateName, TemplateType.TARGET);
                                } catch (Exception e) {
                                    cw.println(e);
                                    return Optional.empty();
                                }
                            })
                    .orElseThrow(() -> new IllegalArgumentException(templateName));
        }

        return enableSelectedEvents(connection, events);
    }

    protected IConstrainedMap<EventOptionID> enableAllEvents(JFRConnection connection)
            throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
            builder.addEvent(eventTypeInfo.getEventTypeID().getFullKey(), "enabled", "true");
        }

        return builder.build();
    }

    protected IConstrainedMap<EventOptionID> enableSelectedEvents(
            JFRConnection connection, String events) throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        Matcher matcher = EVENTS_PATTERN.matcher(events);
        while (matcher.find()) {
            String eventTypeId = matcher.group(1);
            String option = matcher.group(2);
            String value = matcher.group(3);

            builder.addEvent(eventTypeId, option, value);
        }

        return builder.build();
    }

    protected boolean validateEvents(String events) {
        // TODO better validation of entire events string (not just looking for one acceptable
        // setting)
        return TEMPLATE_PATTERN.matcher(events).matches() || EVENTS_PATTERN.matcher(events).find();
    }
}
