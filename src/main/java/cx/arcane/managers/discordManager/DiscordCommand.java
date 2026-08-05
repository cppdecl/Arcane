package cx.arcane.managers.discordManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DiscordCommand {
    String name();
    String description();
    Param[] params() default {};

    @interface Param {
        String name();
        String description();
    }
}