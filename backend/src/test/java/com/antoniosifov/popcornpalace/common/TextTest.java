package com.antoniosifov.popcornpalace.common;

import com.antoniosifov.popcornpalace.common.text.Text;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTest
{
    @Test
    void trimsAndCollapsesWhitespace()
    {
        assertThat(Text.normalize("  The   Matrix \t Reloaded\n")).isEqualTo("The Matrix Reloaded");
    }

    @Test
    void leavesCleanTextUnchanged()
    {
        assertThat(Text.normalize("Hall 1")).isEqualTo("Hall 1");
    }

    @Test
    void turnsWhitespaceOnlyIntoEmpty()
    {
        assertThat(Text.normalize("   ")).isEmpty();
    }

    @Test
    void keepsNull()
    {
        assertThat(Text.normalize(null)).isNull();
    }
}
