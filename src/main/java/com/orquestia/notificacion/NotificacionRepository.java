package com.orquestia.notificacion;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NotificacionRepository extends MongoRepository<Notificacion, String> {
    List<Notificacion> findByUserIdOrderByFechaDesc(String userId);
    long countByUserIdAndLeida(String userId, boolean leida);
}
