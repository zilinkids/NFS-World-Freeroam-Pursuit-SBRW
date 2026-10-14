/*
 * This file is part of the Soapbox Race World core source code.
 * If you use any of this code for third-party purposes, please provide attribution.
 * Copyright (c) 2020.
 */

package com.soapboxrace.core.api;

import com.soapboxrace.core.api.util.Secured;
import com.soapboxrace.core.bo.*;
import com.soapboxrace.core.jpa.EventSessionEntity;
import com.soapboxrace.core.jpa.EventEntity;
import com.soapboxrace.core.jpa.EventMode;
import com.soapboxrace.core.jpa.TokenSessionEntity;
import com.soapboxrace.jaxb.http.LobbyInfo;
import com.soapboxrace.jaxb.http.OwnedCarTrans;
import com.soapboxrace.jaxb.http.SecurityChallenge;
import com.soapboxrace.jaxb.http.SessionInfo;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/matchmaking")
public class MatchMaking {

    @EJB
    private EventBO eventBO;

    @EJB
    private LobbyBO lobbyBO;

    @EJB
    private TokenSessionBO tokenSessionBO;

    @EJB
    private PersonaBO personaBO;

    @EJB
    private MatchmakingBO matchmakingBO;

    @EJB
    private TimeAttackBO timeAttackBO;

    @Inject
    private RequestSessionInfo requestSessionInfo;

    @PUT
    @Secured
    @Path("/joinqueueracenow")
    @Produces(MediaType.APPLICATION_XML)
    public String joinQueueRaceNow() {
        Long activePersonaId = requestSessionInfo.getActivePersonaId();
        OwnedCarTrans defaultCar = personaBO.getDefaultCar(activePersonaId);
        lobbyBO.joinFastLobby(activePersonaId, defaultCar.getCustomCar().getCarClassHash());
        return "";
    }

    @PUT
    @Secured
    @Path("/joinqueueevent/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String joinQueueEvent(@PathParam("eventId") int eventId) {
        EventEntity eventEntity = eventBO.getEvent(eventId);
        if (isHunterEvent(eventEntity)) {
            lobbyBO.joinHunterQueueEvent(requestSessionInfo.getActivePersonaId(), eventId);
        } else {
            lobbyBO.joinQueueEvent(requestSessionInfo.getActivePersonaId(), eventId);
        }
        return "";
    }

    @PUT
    @Secured
    @Path("/joinqueuehunter/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String joinQueueHunter(@PathParam("eventId") int eventId) {
        lobbyBO.joinHunterQueueEvent(requestSessionInfo.getActivePersonaId(), eventId);
        return "";
    }

    @PUT
    @Secured
    @Path("/leavequeue")
    @Produces(MediaType.APPLICATION_XML)
    public String leaveQueue() {
        matchmakingBO.removePlayerFromQueue(requestSessionInfo.getActivePersonaId());
        return "";
    }

    @PUT
    @Secured
    @Path("/leavelobby")
    @Produces(MediaType.APPLICATION_XML)
    public String leavelobby() {
        Long activePersonaId = requestSessionInfo.getActivePersonaId();
        Long activeLobbyId = requestSessionInfo.getActiveLobbyId();
        if (activeLobbyId != null && !activeLobbyId.equals(0L)) {
            lobbyBO.removeEntrantFromLobby(activePersonaId, activeLobbyId);
        }
        return "";
    }

    @GET
    @Secured
    @Path("/launchevent/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public SessionInfo launchEvent(@PathParam("eventId") int eventId) {
        TokenSessionEntity tokenSessionEntity = requestSessionInfo.getTokenSessionEntity();
        EventSessionEntity createEventSession = eventBO.createEventSession(tokenSessionEntity, eventId);
        timeAttackBO.registerClientLaunch(requestSessionInfo.getActivePersonaId(), createEventSession);

        SessionInfo sessionInfo = new SessionInfo();
        SecurityChallenge securityChallenge = new SecurityChallenge();
        securityChallenge.setChallengeId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        securityChallenge.setLeftSize(14);
        securityChallenge.setPattern("FFFFFFFFFFFFFFFF");
        securityChallenge.setRightSize(50);
        sessionInfo.setChallenge(securityChallenge);
        sessionInfo.setEventId(eventId);
        sessionInfo.setSessionId(createEventSession.getId());
        tokenSessionBO.setActiveLobbyId(tokenSessionEntity, 0L);
        return sessionInfo;
    }

    @PUT
    @Secured
    @Path("/makeprivatelobby/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String makePrivateLobby(@PathParam("eventId") int eventId) {
        EventEntity eventEntity = eventBO.getEvent(eventId);
        if (isHunterEvent(eventEntity)) {
            lobbyBO.createPrivateHunterLobby(requestSessionInfo.getActivePersonaId(), eventId);
        } else {
            lobbyBO.createPrivateLobby(requestSessionInfo.getActivePersonaId(), eventId);
        }
        return "";
    }

    private boolean isHunterEvent(EventEntity eventEntity) {
        return eventEntity != null
                && (eventEntity.getEventModeId() == EventMode.HUNTER_VS_RUNNER.getEventModeId()
                || "HunterVsRunner".equals(eventEntity.getName()));
    }

    @PUT
    @Secured
    @Path("/makeprivatehunterlobby/{eventId}")
    @Produces(MediaType.APPLICATION_XML)
    public String makePrivateHunterLobby(@PathParam("eventId") int eventId) {
        lobbyBO.createPrivateHunterLobby(requestSessionInfo.getActivePersonaId(), eventId);
        return "";
    }

    @PUT
    @Secured
    @Path("/acceptinvite")
    @Produces(MediaType.APPLICATION_XML)
    public LobbyInfo acceptInvite(@QueryParam("lobbyInviteId") Long lobbyInviteId) {
        tokenSessionBO.setActiveLobbyId(requestSessionInfo.getTokenSessionEntity(), lobbyInviteId);
        return lobbyBO.acceptinvite(requestSessionInfo.getActivePersonaId(), lobbyInviteId);
    }

    @PUT
    @Secured
    @Path("/declineinvite")
    @Produces(MediaType.APPLICATION_XML)
    public String declineInvite(@QueryParam("lobbyInviteId") Long lobbyInviteId) {
        lobbyBO.declineinvite(requestSessionInfo.getActivePersonaId(), lobbyInviteId);
        return "";
    }

}
