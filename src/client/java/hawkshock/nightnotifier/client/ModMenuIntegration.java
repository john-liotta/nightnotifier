package hawkshock.nightnotifier.client;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import hawkshock.nightnotifier.client.ui.ClothConfigFactory;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Dynamically choose Cloth Config screen when available; fallback to custom screen
		return ClothConfigFactory::create;
	}
}