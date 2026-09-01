package com.mygamehub.gamefinder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface GameTagRepository extends JpaRepository<GameTag,Long>{
    List<GameTag> findByCanonicalNameIn(Collection<String> names);

    @Query("select t from GameTag t where :query = '' or lower(t.canonicalName) like lower(concat('%', :query, '%')) or lower(t.displayNameKo) like lower(concat('%', :query, '%')) order by t.canonicalName")
    List<GameTag> autocomplete(@Param("query") String query, Pageable pageable);
}
