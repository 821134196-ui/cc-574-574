package org.acme.contracts.rest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.acme.contracts.model.Enums;
import org.acme.contracts.model.Reminder;
import org.acme.contracts.security.CurrentUser;

import java.util.List;
import java.util.Map;

@Path("/api/reminders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReminderResource {

    @Inject
    CurrentUser currentUser;

    @GET
    public List<java.util.Map<String, Object>> list(@QueryParam("ack") Boolean acknowledged) {
        currentUser.requireAny();
        java.util.List<Reminder> list = acknowledged != null
                ? Reminder.list("acknowledged = ?1 order by createdAt desc", acknowledged)
                : Reminder.list("order by createdAt desc");
        return list.stream().map(org.acme.contracts.service.Views::reminder).toList();
    }

    @POST
    @Path("/{id}/ack")
    @Transactional
    public Map<String, Object> ack(@PathParam("id") Long id) {
        currentUser.requireRole(Enums.Role.ADMIN);
        Reminder r = Reminder.findById(id);
        if (r == null) {
            throw org.acme.contracts.service.ApiException.notFound("提醒不存在: " + id);
        }
        r.acknowledged = true;
        return Map.of("id", r.id, "acknowledged", true);
    }
}
