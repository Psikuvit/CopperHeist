package me.psikuvit.copperHeist.shop.action;

/** Builds a new golem at the buyer's dock, up to golems.cap. */
public class SpawnGolemAction implements ShopAction {

    @Override
    public String check(ShopPurchase purchase) {
        int cap = purchase.plugin().settings().getInt("golems.cap", 4);
        if (purchase.game().getTeam(purchase.gamePlayer().getTeam()).getGolems().size() >= cap) {
            return "actionbar.golem-cap-reached";
        }
        return null;
    }

    @Override
    public void perform(ShopPurchase purchase) {
        purchase.game().getGolemManager().spawnOne(purchase.gamePlayer().getTeam());
    }
}
