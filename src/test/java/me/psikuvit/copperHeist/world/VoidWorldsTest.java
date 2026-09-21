package me.psikuvit.copperHeist.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidWorldsTest {

    @Test
    void ordinaryWorldNamesAreAccepted() {
        assertTrue(VoidWorlds.validName("heist_foundry"));
        assertTrue(VoidWorlds.validName("Arena-2"));
        assertTrue(VoidWorlds.validName("a".repeat(32)));
    }

    @Test
    void namesThatCouldReachOutsideTheServerFolderAreRefused() {
        assertFalse(VoidWorlds.validName("../secret"));
        assertFalse(VoidWorlds.validName("..\\secret"));
        assertFalse(VoidWorlds.validName("a/b"));
        assertFalse(VoidWorlds.validName("C:\\worlds"));
        assertFalse(VoidWorlds.validName(".."));
        assertFalse(VoidWorlds.validName(""));
        assertFalse(VoidWorlds.validName(null));
        assertFalse(VoidWorlds.validName("a".repeat(33)));
        assertFalse(VoidWorlds.validName("with space"));
    }
}
