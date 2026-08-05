package cx.arcane.managers.commandManager.arguments.enchantmentArgument;

import lombok.Getter;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

public class EnchantmentArgument {

    private final Enchantment enchantment;
    @Getter
    private final String name;

    public EnchantmentArgument(Enchantment enchantment, String name) {
        this.enchantment = enchantment; this.name = name;
    }

    public @NotNull Enchantment get() {
        return enchantment;
    }
}