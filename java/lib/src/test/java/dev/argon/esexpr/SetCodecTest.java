package dev.argon.esexpr;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.codecs.ImmutableSetCodec;
import dev.argon.esexpr.codecs.SetCodec;
import dev.argon.esexpr.codecs.StringCodec;
import dev.argon.esexpr.ESExprCodec.FailurePath;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SetCodecTest {

    @Test
    public void testSetCodec() throws DecodeException {
        var codec = new SetCodec<>(StringCodec.INSTANCE);
        var set = new HashSet<String>();
        set.add("a");
        set.add("b");

        var encoded = codec.encode(set);
        assertTrue(encoded instanceof ESExpr.Constructor);
        var constructor = (ESExpr.Constructor) encoded;
        assertEquals("set", constructor.constructor());
        assertEquals(2, constructor.args().size());

        var decoded = codec.decode(encoded, new FailurePath.Current());
        assertEquals(set, decoded);
    }

    @Test
    public void testImmutableSetCodec() throws DecodeException {
        var codec = new ImmutableSetCodec<>(StringCodec.INSTANCE);
        var set = ImmutableSet.of("a", "b");

        var encoded = codec.encode(set);
        var decoded = codec.decode(encoded, new FailurePath.Current());
        assertEquals(set, decoded);
    }

    @Test
    public void testIsEncodedEqual() {
        var codec = new SetCodec<>(StringCodec.INSTANCE);
        var set1 = Set.of("a", "b");
        var set2 = new HashSet<String>();
        set2.add("b");
        set2.add("a");

        assertTrue(codec.isEncodedEqual(set1, set2));

        var set3 = Set.of("a", "c");
        assertFalse(codec.isEncodedEqual(set1, set3));
        
        var set4 = Set.of("a", "b", "c");
        assertFalse(codec.isEncodedEqual(set1, set4));
    }
}
