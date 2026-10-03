package com.example.npc.core.model

import com.example.npc.core.model.classify.PackageGatedRouter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackageGatedRouterTest {

    @Test
    fun `companion constants contain required bank packages`() {
        assertTrue(PackageGatedRouter.BANK_PACKAGES.contains("com.apb.mobile"))
        assertTrue(PackageGatedRouter.BANK_PACKAGES.contains("com.prisbank.app"))
        assertTrue(PackageGatedRouter.BANK_PACKAGES.contains("md.maib.maibank"))
    }

    @Test
    fun `companion constants contain required bank SMS senders`() {
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("APB"))
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("AGROPROMBANK"))
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("PRISBANK"))
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("SBERBANK"))
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("MAIB"))
        assertTrue(PackageGatedRouter.BANK_SMS_SENDERS.contains("900"))
    }

    @Test
    fun `companion constants contain required messenger packages`() {
        assertTrue(PackageGatedRouter.MESSENGER_PACKAGES.contains("com.radolyn.ayugram"))
        assertTrue(PackageGatedRouter.MESSENGER_PACKAGES.contains("org.telegram.messenger"))
        assertTrue(PackageGatedRouter.MESSENGER_PACKAGES.contains("com.whatsapp"))
        assertTrue(PackageGatedRouter.MESSENGER_PACKAGES.contains("com.viber.voip"))
    }
}
