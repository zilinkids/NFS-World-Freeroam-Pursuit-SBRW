package com.soapboxrace.core.bo;

import com.soapboxrace.core.dao.EventSessionDAO;
import com.soapboxrace.core.dao.PersonaDAO;
import com.soapboxrace.core.dao.UsedPowerupDAO;
import com.soapboxrace.core.jpa.UsedPowerupEntity;

import javax.ejb.EJB;
import javax.ejb.Stateless;

@Stateless
public class PowerupTrackingBO {

    @EJB
    private UsedPowerupDAO usedPowerupDAO;

    @EJB
    private EventSessionDAO eventSessionDAO;

    @EJB
    private PersonaDAO personaDAO;

    @EJB
    private PursuitProbeBO pursuitProbeBO;

    public void createPowerupRecord(Long eventSessionId, Long activePersonaId, Integer powerupHash) {
        UsedPowerupEntity usedPowerupEntity = new UsedPowerupEntity();
        usedPowerupEntity.setPersonaEntity(personaDAO.find(activePersonaId));
        usedPowerupEntity.setPowerupHash(powerupHash);

        if (eventSessionId != null) {
            usedPowerupEntity.setEventSessionEntity(eventSessionDAO.find(eventSessionId));
        }

        usedPowerupDAO.insert(usedPowerupEntity);

        // 自由漫游逃脱（PursuitProbe）用到的"本局道具使用统计"。
        pursuitProbeBO.recordPowerup(activePersonaId, powerupHash);
    }
}
