package org.jboss.remoting.core.security.sasl;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.sasl.SaslException;

/**
 *
 */
public final class SrpOptions {
    private final Set<String> mdaSet = new LinkedHashSet<String>();
    private final Set<String> integritySet = new LinkedHashSet<String>();
    private final Set<String> confidentialitySet = new LinkedHashSet<String>();
    private boolean replayDetection;
    private boolean integrityManditory;
    private boolean replayDetectionManditory;
    private boolean confidentialityManditory;
    private long maxBufferSize;

    public SrpOptions() {
    }

    public SrpOptions(final String options) throws SaslException {
        int i = 0;
        boolean done = false;
        do {
            int end;
            int comma = options.indexOf(',', i);
            if (comma == -1) {
                end = options.length();
                done = true;
            } else {
                end = comma;
            }
            int equals = options.indexOf('=', i);
            if (equals == -1 || equals > end) {
                // one-part option
                final String option = options.substring(i, end);
                if ("replay_detection".equals(option)) {
                    replayDetection = true;
                } else {
                    throw new SaslException("Invalid option string received from server: \"" + option + "\"");
                }
            } else {
                final String option = options.substring(i, equals);
                final String value = options.substring(equals + 1, end);
                if ("mda".equals(option)) {
                    mdaSet.add(value);
                } else if ("integrity".equals(option)) {
                    integritySet.add(value);
                } else if ("confidentiality".equals(option)) {
                    confidentialitySet.add(value);
                } else if ("maxbuffersize".equals(option)) {
                    try {
                        maxBufferSize = Long.parseLong(value);
                    } catch (NumberFormatException ex) {
                        throw new SaslException("Invalid value for maxbuffersize option", ex);
                    }
                } else if ("mandatory".equals(option)) {
                    if ("integrity".equals(value)) {
                        integrityManditory = true;
                    } else if ("replay_detection".equals(value)) {
                        replayDetectionManditory = true;
                    } else if ("confidentiality".equals(value)) {
                        confidentialityManditory = true;
                    } else {
                        throw new SaslException("Invalid value for mandatory option: \"" + value + "\"");
                    }
                } else {
                    throw new SaslException("Invalid option string received from server: \"" + option + "\"");
                }
            }
            i = end + 1;
        } while (! done);
    }

    public Set<String> getMdaSet() {
        return mdaSet;
    }

    public Set<String> getIntegritySet() {
        return integritySet;
    }

    public Set<String> getConfidentialitySet() {
        return confidentialitySet;
    }

    public boolean isReplayDetection() {
        return replayDetection;
    }

    public void setReplayDetection(final boolean replayDetection) {
        this.replayDetection = replayDetection;
    }

    public boolean isIntegrityManditory() {
        return integrityManditory;
    }

    public void setIntegrityManditory(final boolean integrityManditory) {
        this.integrityManditory = integrityManditory;
    }

    public boolean isReplayDetectionManditory() {
        return replayDetectionManditory;
    }

    public void setReplayDetectionManditory(final boolean replayDetectionManditory) {
        this.replayDetectionManditory = replayDetectionManditory;
    }

    public boolean isConfidentialityManditory() {
        return confidentialityManditory;
    }

    public void setConfidentialityManditory(final boolean confidentialityManditory) {
        this.confidentialityManditory = confidentialityManditory;
    }

    public long getMaxBufferSize() {
        return maxBufferSize;
    }

    public void setMaxBufferSize(final long maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String mda : mdaSet) {
            if (!first) {
                builder.append(',');
            }
            builder.append("mda=");
            builder.append(mda);
            first = false;
        }
        for (String integrity : integritySet) {
            if (!first) {
                builder.append(',');
            }
            builder.append("integrity=");
            builder.append(integrity);
            first = false;
        }
        for (String confidentiality : confidentialitySet) {
            if (!first) {
                builder.append(',');
            }
            builder.append("confidentiality=");
            builder.append(confidentiality);
            first = false;
        }
        if (replayDetection) {
            if (!first) {
                builder.append(',');
            }
            builder.append("replay_detection");
            first = false;
        }
        if (integrityManditory) {
            if (!first) {
                builder.append(',');
            }
            builder.append("mandatory=integrity");
            first = false;
        }
        if (replayDetectionManditory) {
            if (!first) {
                builder.append(',');
            }
            builder.append("mandatory=replay_detection");
            first = false;
        }
        if (confidentialityManditory) {
            if (!first) {
                builder.append(',');
            }
            builder.append("mandatory=confidentiality");
            first = false;
        }
        if (maxBufferSize != 0L) {
            if (!first) {
                builder.append(',');
            }
            builder.append("maxbuffersize=");
            builder.append(maxBufferSize);
            first = false;
        }
        return builder.toString();
    }
}
