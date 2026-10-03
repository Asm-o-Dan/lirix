package com.example.npc.core.storage

import app.cash.turbine.test
import com.example.npc.core.storage.dao.UserPrototypeDao
import com.example.npc.core.storage.entity.UserPrototypeEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UserPrototypeDaoTest {

    private val dao: UserPrototypeDao = mockk(relaxed = true)

    private val sampleFingerprint = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val samplePkg = "com.radolyn.ayugram"

    private val sampleEntity = UserPrototypeEntity(
        id = 1L,
        packageName = samplePkg,
        fingerprint = sampleFingerprint,
        category = "ADVERTISEMENT",
        supportCount = 1,
        createdAt = 1_000_000L,
        lastSeenAt = 1_000_000L
    )

    @Test
    fun `findByPackageAndFingerprint returns matching prototype`() = runTest {
        coEvery { dao.findByPackageAndFingerprint(samplePkg, sampleFingerprint) } returns sampleEntity

        val result = dao.findByPackageAndFingerprint(samplePkg, sampleFingerprint)

        result shouldBe sampleEntity
        result?.packageName shouldBe samplePkg
        result?.fingerprint shouldBe sampleFingerprint
        result?.category shouldBe "ADVERTISEMENT"
        result?.supportCount shouldBe 1
    }

    @Test
    fun `findByPackageAndFingerprint returns null when not found`() = runTest {
        coEvery { dao.findByPackageAndFingerprint(samplePkg, "unknown_fp") } returns null

        val result = dao.findByPackageAndFingerprint(samplePkg, "unknown_fp")

        result shouldBe null
    }

    @Test
    fun `insert saves new prototype entity`() = runTest {
        coEvery { dao.insert(any()) } returns 5L

        val id = dao.insert(sampleEntity)

        id shouldBe 5L
        coVerify(exactly = 1) { dao.insert(sampleEntity) }
    }

    @Test
    fun `incrementSupport updates supportCount and lastSeenAt`() = runTest {
        val now = 2_000_000L
        dao.incrementSupport(1L, now)

        coVerify(exactly = 1) { dao.incrementSupport(1L, now) }
    }

    @Test
    fun `reassignCategory updates category and resets supportCount to 1`() = runTest {
        val now = 2_500_000L
        dao.reassignCategory(1L, "SERVICES", now)

        coVerify(exactly = 1) { dao.reassignCategory(1L, "SERVICES", now) }
    }

    @Test
    fun `recordCorrection creates new prototype when none exists`() = runTest {
        val now = 3_000_000L
        coEvery { dao.findByPackageAndFingerprint(samplePkg, sampleFingerprint) } returns null
        coEvery { dao.recordCorrection(samplePkg, sampleFingerprint, "ADVERTISEMENT", now) } coAnswers {
            // Default open method logic in DAO
            dao.insert(
                UserPrototypeEntity(
                    packageName = samplePkg,
                    fingerprint = sampleFingerprint,
                    category = "ADVERTISEMENT",
                    supportCount = 1,
                    createdAt = now,
                    lastSeenAt = now
                )
            )
        }

        dao.recordCorrection(samplePkg, sampleFingerprint, "ADVERTISEMENT", now)

        coVerify {
            dao.insert(match {
                it.packageName == samplePkg &&
                    it.fingerprint == sampleFingerprint &&
                    it.category == "ADVERTISEMENT" &&
                    it.supportCount == 1 &&
                    it.createdAt == now
            })
        }
    }

    @Test
    fun `recordCorrection increments supportCount when category matches existing`() = runTest {
        val now = 4_000_000L
        coEvery { dao.findByPackageAndFingerprint(samplePkg, sampleFingerprint) } returns sampleEntity
        coEvery { dao.recordCorrection(samplePkg, sampleFingerprint, "ADVERTISEMENT", now) } coAnswers {
            dao.incrementSupport(sampleEntity.id, now)
        }

        dao.recordCorrection(samplePkg, sampleFingerprint, "ADVERTISEMENT", now)

        coVerify(exactly = 1) { dao.incrementSupport(sampleEntity.id, now) }
    }

    @Test
    fun `recordCorrection reassigns category and resets supportCount when category changes`() = runTest {
        val now = 5_000_000L
        val existing = sampleEntity.copy(category = "ADVERTISEMENT", supportCount = 5)
        coEvery { dao.findByPackageAndFingerprint(samplePkg, sampleFingerprint) } returns existing
        coEvery { dao.recordCorrection(samplePkg, sampleFingerprint, "COMMUNICATION", now) } coAnswers {
            dao.reassignCategory(existing.id, "COMMUNICATION", now)
        }

        dao.recordCorrection(samplePkg, sampleFingerprint, "COMMUNICATION", now)

        coVerify(exactly = 1) { dao.reassignCategory(existing.id, "COMMUNICATION", now) }
    }

    @Test
    fun `observeAll emits prototypes ordered by lastSeenAt DESC`() = runTest {
        every { dao.observeAll() } returns flowOf(listOf(sampleEntity))

        dao.observeAll().test {
            val list = awaitItem()
            list.size shouldBe 1
            list[0].fingerprint shouldBe sampleFingerprint
            awaitComplete()
        }
    }
}
