/*
 * 自由漫游逃脱（NFSWorldPursuitProbe ASI）的结算接口，接口风格与 Lawin 版 PursuitProbe.js 一致。
 * ASI 直连本服务（WinHttp，无登录令牌），通过 personaId 定位要入账的档案。
 *
 * 端点：
 *   POST /Engine.svc/pursuitprobe/reward?personaId=N&busted=0|1&cash=N&rep=N&heat=F&item=<tag>&qty=N
 *   POST /Engine.svc/pursuitprobe/powerups/reset?personaId=N   开始统计本局使用的道具
 *   GET  /Engine.svc/pursuitprobe/powerups?personaId=N         读取 "hash count" 行并停止统计
 */
package com.soapboxrace.core.api;

import com.soapboxrace.core.bo.PursuitProbeBO;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/pursuitprobe")
public class PursuitProbeService {

    @EJB
    private PursuitProbeBO pursuitProbeBO;

    @POST
    @Path("/reward")
    @Produces(MediaType.APPLICATION_XML)
    public String reward(@QueryParam("personaId") Long personaId,
                         @QueryParam("busted") Integer busted,
                         @QueryParam("cash") Integer cash,
                         @QueryParam("rep") Integer rep,
                         @QueryParam("heat") Float heat,
                         @QueryParam("item") String item,
                         @QueryParam("qty") Integer qty) {
        PursuitProbeBO.Result r = pursuitProbeBO.saveReward(
                personaId,
                busted != null && busted == 1,
                cash == null ? 0 : cash,
                rep == null ? 0 : rep,
                heat == null ? 1.0f : heat,
                item,
                qty == null ? 1 : qty);
        return "<PursuitProbeReward>"
                + "<Cash>" + r.cash + "</Cash>"
                + "<Rep>" + r.rep + "</Rep>"
                + "<Level>" + r.level + "</Level>"
                + "<LeveledUp>" + r.leveledUp + "</LeveledUp>"
                + "<Item>" + r.item + "</Item>"
                + "</PursuitProbeReward>";
    }

    @POST
    @Path("/powerups/reset")
    public Response resetPowerups(@QueryParam("personaId") Long personaId) {
        pursuitProbeBO.resetPowerups(personaId);
        return Response.ok().build();
    }

    @GET
    @Path("/powerups")
    @Produces(MediaType.TEXT_PLAIN)
    public String powerups(@QueryParam("personaId") Long personaId) {
        return pursuitProbeBO.readAndClearPowerups(personaId);
    }
}
