/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.core.base;

import cn.ponfee.disjob.common.util.Jsons;
import cn.ponfee.disjob.common.util.Numbers;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.collections4.MapUtils;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static cn.ponfee.disjob.common.base.Symbol.Str.COLON;
import static cn.ponfee.disjob.common.collect.Collects.get;

/**
 * Supervisor definition
 *
 * @author Ponfee
 */
@JsonDeserialize(using = Supervisor.JacksonDeserializer.class)
public final class Supervisor extends Server {
    private static final long serialVersionUID = -1254559108807415145L;

    private transient final String serializedValue;

    public Supervisor(String host, int port) {
        super(host, port);
        this.serializedValue = host + COLON + port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String serialize() {
        return serializedValue;
    }

    /**
     * Deserialize from string.
     *
     * @param text the serialized text string
     * @return supervisor object of the text deserialized result
     */
    public static Supervisor deserialize(String text) {
        Assert.hasText(text, "Serialized text cannot empty.");
        String[] array = text.split(COLON);

        String host = get(array, 0);
        Assert.hasText(host, "Supervisor host cannot bank.");

        int port = Numbers.toInt(get(array, 1));

        return new Supervisor(host, port);
    }

    public static Supervisor current() {
        return Current.current;
    }

    // --------------------------------------------------------custom jackson deserialize

    /**
     * Custom deserialize Supervisor based jackson.
     */
    public static class JacksonDeserializer extends JsonDeserializer<Supervisor> {
        @Override
        public Supervisor deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            return of(p.readValueAs(Jsons.MAP_NORMAL));
        }
    }

    private static Supervisor of(Map<String, Object> map) {
        if (map == null) {
            return null;
        }

        String host = MapUtils.getString(map, "host");
        int port = MapUtils.getIntValue(map, "port");
        return new Supervisor(host, port);
    }

    /**
     * Supervisor.class.getDeclaredClasses()[0]
     */
    private static class Current {
        private static volatile Supervisor current;

        private static synchronized void set(Supervisor supervisor) {
            if (supervisor == null) {
                throw new AssertionError("Current supervisor cannot set null.");
            }
            if (current != null) {
                throw new AssertionError("Current supervisor already set.");
            }
            current = supervisor;
        }
    }

}
