package me.psikuvit.copperHeist.shop.action;

/** What buying a shop entry actually does. Register more through {@link ShopActionRegistry}. */
public interface ShopAction {

    /** Called before the player is charged; return a messages.yml key to refuse the purchase, or null to allow it. */
    default String check(ShopPurchase purchase) {
        return null;
    }

    /** Called after payment. */
    void perform(ShopPurchase purchase);
}
