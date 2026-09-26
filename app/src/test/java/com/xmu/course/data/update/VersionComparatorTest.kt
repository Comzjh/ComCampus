package com.xmu.course.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test fun `v prefix is ignored`() = assertEquals(0, VersionComparator.compare("v1.2.3", "1.2.3"))
    @Test fun `numeric components are compared numerically`() = assertTrue(VersionComparator.isNewer("1.9.0", "1.10.0"))
    @Test fun `equal versions are not newer`() = assertFalse(VersionComparator.isNewer("1.2.3", "1.2.3"))
    @Test fun `lower candidate is not newer`() = assertFalse(VersionComparator.isNewer("1.2.4", "1.2.3"))
    @Test fun `major version wins`() = assertTrue(VersionComparator.isNewer("1.99.0", "2.0.0"))
    @Test fun `invalid tag is unknown`() = assertNull(VersionComparator.compare("dev", "1.2.3"))

    @Test fun `stable numeric ordering preserved`() {
        assertTrue(VersionComparator.isNewer("0.7.1", "0.8.0"))
        assertTrue(VersionComparator.isNewer("0.8.0", "1.0.0"))
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", "0.8.0"))
    }

    @Test fun `prerelease labels rank alpha then beta then rc`() {
        assertTrue(VersionComparator.isNewer("0.8.0-alpha", "0.8.0-beta"))
        assertTrue(VersionComparator.isNewer("0.8.0-beta", "0.8.0-rc1"))
    }

    @Test fun `rc numbers rank`() {
        assertTrue(VersionComparator.isNewer("0.8.0-rc1", "0.8.0-rc2"))
        assertFalse(VersionComparator.isNewer("0.8.0-rc2", "0.8.0-rc1"))
    }

    @Test fun `stable outranks prerelease with same core`() {
        assertTrue(VersionComparator.isNewer("0.8.0-rc2", "0.8.0"))
        assertFalse(VersionComparator.isNewer("0.8.0", "0.8.0-rc2"))
    }

    @Test fun `cross core numeric wins over prerelease rank`() {
        assertTrue(VersionComparator.isNewer("0.8.0-rc9", "0.9.0-alpha"))
        assertTrue(VersionComparator.isNewer("0.8.0", "0.9.0-alpha"))
        assertFalse(VersionComparator.isNewer("0.9.0-alpha", "0.8.0"))
    }

    @Test fun `build metadata does not affect precedence`() {
        assertEquals(0, VersionComparator.compare("0.8.0+1", "0.8.0+2"))
        assertEquals(0, VersionComparator.compare("0.8.0-rc1+1", "0.8.0-rc1+2"))
        assertFalse(VersionComparator.isNewer("0.8.0+abc", "0.8.0+def"))
    }

    @Test fun `bare label ranks below numbered label`() {
        assertTrue(VersionComparator.isNewer("0.8.0-rc", "0.8.0-rc1"))
    }

    @Test fun `unknown suffix is deterministic and below stable`() {
        assertTrue(VersionComparator.isNewer("0.8.0-dev", "0.8.0"))
        assertEquals(0, VersionComparator.compare("0.8.0-dev", "0.8.0-dev"))
        assertTrue(VersionComparator.isNewer("0.8.0-alpha", "0.8.0-dev"))
        assertTrue(VersionComparator.isNewer("0.8.0-dev", "0.8.0-rc1"))
    }

    @Test fun `update checker boundary contract with prerelease`() {
        // GitHubUpdateRepository: isNewer -> Available; compare == null -> Unknown.
        val current = "0.7.1-alpha"
        val newerStable = "0.8.0-rc1"
        assertTrue(VersionComparator.isNewer(current, newerStable))
        assertNull(VersionComparator.compare(current, "not-a-version"))
        assertFalse(VersionComparator.isNewer("0.8.0-rc1", "0.8.0-rc1"))
    }
}
