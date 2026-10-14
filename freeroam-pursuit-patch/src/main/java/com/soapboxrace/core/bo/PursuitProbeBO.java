/*
 * 自由漫游逃脱（NFSWorldPursuitProbe ASI）的结算业务逻辑，接口风格与 Lawin 版 PursuitProbe.js 一致：
 * ASI 自己按 event 385 公式算出奖励，再把这些奖励存进档案（现金 / REP / 车辆热度与耐久 / 幸运抽奖道具）。
 *
 * 幂等性说明：/reward 的语义是"把这一局的结果落库"，和 /event/arbitration、/event/bust 相同，
 * 调用方（ASI）保证每局只调用一次。
 */
package com.soapboxrace.core.bo;

import com.soapboxrace.core.dao.CarDAO;
import com.soapboxrace.core.dao.PersonaDAO;
import com.soapboxrace.core.dao.ProductDAO;
import com.soapboxrace.core.engine.EngineException;
import com.soapboxrace.core.engine.EngineExceptionCode;
import com.soapboxrace.core.jpa.CarEntity;
import com.soapboxrace.core.jpa.InventoryEntity;
import com.soapboxrace.core.jpa.PersonaEntity;
import com.soapboxrace.core.jpa.ProductEntity;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Stateless
public class PursuitProbeBO {

    // 道具使用计数（按 personaId）。与 Lawin 版一致：reset 后开始计数，
    // 读取时返回 "hash count" 行并清空。游戏每次发射道具都会走 PowerupTrackingBO.createPowerupRecord，
    // 那里会调用 recordPowerup，只对处于计数状态的 persona 生效。
    private static final Map<Long, Map<Integer, Integer>> POWERUP_COUNTS = new ConcurrentHashMap<>();

    @EJB
    private PersonaDAO personaDAO;

    @EJB
    private PersonaBO personaBO;

    @EJB
    private CarDAO carDAO;

    @EJB
    private RewardBO rewardBO;

    @EJB
    private InventoryBO inventoryBO;

    @EJB
    private ProductDAO productDAO;

    public void resetPowerups(Long personaId) {
        if (personaId != null) {
            POWERUP_COUNTS.put(personaId, new ConcurrentHashMap<>());
        }
    }

    public void recordPowerup(Long personaId, Integer hash) {
        Map<Integer, Integer> counts = POWERUP_COUNTS.get(personaId);
        if (counts != null && hash != null) {
            counts.merge(hash, 1, Integer::sum);
        }
    }

    public String readAndClearPowerups(Long personaId) {
        if (personaId == null) {
            return "";
        }
        Map<Integer, Integer> counts = POWERUP_COUNTS.remove(personaId);
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            sb.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    public static class Result {
        public int cash;
        public int rep;
        public int level;
        public boolean leveledUp;
        public String item = "";
    }

    public Result saveReward(Long personaId, boolean busted, int cash, int rep, float heat, String itemTag, int qty) {
        PersonaEntity persona = personaDAO.find(personaId);
        if (persona == null) {
            throw new EngineException("Unknown personaId.", EngineExceptionCode.InvalidOperation, true);
        }

        Result result = new Result();
        result.level = persona.getLevel();

        int appliedHeat = Math.round(heat);
        if (busted || appliedHeat < 1) {
            appliedHeat = 1;
        }
        if (appliedHeat > 5) {
            appliedHeat = 5;
        }

        // 逃脱才有奖励；applyRaceReward 内部按 ENABLE_ECONOMY / ENABLE_REPUTATION 参数处理现金与升级。
        if (!busted && (cash > 0 || rep > 0)) {
            int levelBefore = persona.getLevel();
            rewardBO.applyRaceReward(rep, cash, persona, true, null);
            result.cash = cash;
            result.rep = rep;
            result.level = persona.getLevel();
            result.leveledUp = persona.getLevel() > levelBefore;
        }

        // 车辆热度 = 本局热度（被捕则 1），耐久 -5（不低于 0）。
        CarEntity car = personaBO.getDefaultCarEntity(personaId);
        if (car != null) {
            int durability = Math.max(0, car.getDurability() - 5);
            car.setDurability(durability);
            car.setHeat(appliedHeat);
            carDAO.update(car);
        }

        // 幸运抽奖道具（逃脱才有）：按 entitlementTag 加入库存。
        if (!busted && itemTag != null && !itemTag.isEmpty()) {
            ProductEntity product = productDAO.findByEntitlementTag(itemTag);
            if (product != null) {
                int q = qty > 0 ? qty : 1;
                InventoryEntity inventory = inventoryBO.getInventory(persona);
                if ("POWERUP".equals(product.getProductType())) {
                    inventoryBO.addStackedInventoryItem(inventory, product.getProductId(), q);
                } else {
                    inventoryBO.addInventoryItem(inventory, product.getProductId(), q);
                }
                result.item = itemTag + " x" + q;
            }
        }

        return result;
    }
}
