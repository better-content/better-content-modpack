# Food progression

The Diet screen shows eight named groups. Six lasting groups unlock an ability at 20%, then add distinct abilities at 40%, 60%, and 80%. Sweetness and Draught are temporary loads. Prepared food can credit every group supplied by its ingredients, even when the result also has a direct Diet tag.

| Group | First dependable source | Later food and role |
| --- | --- | --- |
| Sinew | Meat, fish, eggs | Charged strikes, then stagger and cleave |
| Sweetness | Fruit, honey | Multiplies unlocked benefits; hunger and nutrition drain grow as `8^load` |
| Granary | Bumblezone grain seed line | Correct-tool mining rhythm and a work burst |
| Orchard | Wild fruit and berries | Safer travel, stepping, stride, and a sprinting leap |
| Richness | Meat and dairy fat | Hunger, thirst, and stamina savings; emergency reserve |
| Garden | Bumblezone vegetable seed line | Heat and cold protection, knockback guard |
| Cream | Cow milk | Meal recovery, milk effect preservation, cleansing |
| Draught | Fermented drinks | Up to 40% damage reduction with rising impairment and a blackout at full load |

The Overworld supplies wild fruit and animal foods. Farmer's Delight's seven wild crop placements are disabled at their native biome modifier IDs. Cultivatable grain and vegetable stock comes from the Bumblezone. Fruits Delight provides forage plants and cooking recipes, while its Dynamic Trees addon gives the fruit trees the pack's tree behavior. Farmer's Delight fruit salad is available from early fruit and honey; a cooking pot can make fig chicken stew with milk, and later pineapple fried rice combines cultivated grain, vegetables, eggs, meat, and fruit. See the actual recipes in EMI for exact counts and tools.

Milk drinking directly awards Cream and Richness because a milk bucket is not an edible food item in Diet. Fruits Delight foods retain their distinct utility effects; newly applied vanilla beneficial effects are limited to level I and 60 seconds, while Health Boost, Cycling's experience orbs, and Digesting's overflow nutrition are suppressed. Existing stronger effects are preserved.

Sweetness amplifies the six lasting groups' unlocked effects linearly with `1 + load`; the hunger cost and both native and upper-band nutrition decay scale with `8^load`. Draught reduces incoming damage by `min(40%, 40% × load × Sweetness amplification)`. Its control and work penalties begin above 30% and rise to severe impairment at full load. At 100% the player blacks out until load drops to 85%.
