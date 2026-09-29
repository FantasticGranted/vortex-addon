package me.aleksilassila.litematica.printer.config.builder;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBase;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigMetadata;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public abstract class BaseConfigBuilder<T extends ConfigBase<?>, B extends BaseConfigBuilder<T, B>> {
    protected final I18n i18n;
    protected final CopyOnWriteArrayList<Consumer<IConfigBase>> valueChangeCallbacks = new CopyOnWriteArrayList<>();
    protected String nameKey;
    protected String descKey;
    protected @Nullable BooleanSupplier visible;

    public BaseConfigBuilder(I18n i18n) {
        this.i18n = i18n;
        this.nameKey = i18n.getConfigNameKey();
        this.descKey = i18n.getConfigDescKey();
        this.visible = null;
    }

    public BaseConfigBuilder(String translateKey) {
        this(I18n.of(translateKey));
    }

    public B translationAlias(String alias) {
        I18n i18n = I18n.of(alias);
        this.nameKey = i18n.getConfigNameKey();
        this.descKey = i18n.getConfigDescKey();
        return (B) this;
    }

    public B setNameKey(String name) {
        this.nameKey = name;
        return (B) this;
    }

    public B setDescKey(String comment) {
        this.descKey = comment;
        return (B) this;
    }

    public B setVisible(boolean visible) {
        this.visible = () -> visible;
        return (B) this;
    }

    public B setVisible(@Nullable BooleanSupplier visible) {
        this.visible = visible;
        return (B) this;
    }

    public B addValueChangeListener(Consumer<IConfigBase> callback) {
        if (callback != null) {
            this.valueChangeCallbacks.add(callback);
        }
        return (B) this;
    }

    protected T buildExtension(T config) {
        buildMetadata(config);
        buildValueChangeCallbacks(config);
        return config;
    }

    protected void buildMetadata(T config) {
        ConfigMetadata.register(config, this.nameKey, this.descKey, this.visible);
    }

    protected void buildValueChangeCallbacks(T config) {
        if (!this.valueChangeCallbacks.isEmpty()) {
            config.setValueChangeCallback(changed -> {
                for (Consumer<IConfigBase> callback : this.valueChangeCallbacks) {
                    callback.accept(changed);
                }
            });
        }
    }

    public abstract T build();
}
