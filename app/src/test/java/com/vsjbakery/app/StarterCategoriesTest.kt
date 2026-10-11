package com.vsjbakery.app

import com.vsjbakery.app.domain.model.StarterCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarterCategoriesTest {

    @Test
    fun containsTheSixConfirmedCategoriesInOrder() {
        assertEquals(
            listOf("Cakes", "Pastries", "Breads", "Cookies", "Snacks", "Beverages"),
            StarterCategories.all.map { it.name },
        )
    }

    @Test
    fun slugsAreUnique() {
        val slugs = StarterCategories.all.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
    }

    @Test
    fun lookupBySlug() {
        assertEquals("Breads", StarterCategories.bySlug("breads")?.name)
        assertNull(StarterCategories.bySlug("unknown"))
    }
}
