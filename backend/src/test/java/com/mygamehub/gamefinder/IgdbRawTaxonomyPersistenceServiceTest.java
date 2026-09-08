package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;

class IgdbRawTaxonomyPersistenceServiceTest {
    @Test
    void jdbcPathUsesBoundedBatchWritesAndKeepsExistingRelations() {
        var terms = mock(IgdbTaxonomyTermRepository.class);
        var relations = mock(SteamGameIgdbTermRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var game = new SteamGame(10, "A", 0, 0);
        var horror = new IgdbTaxonomyTerm(value(IgdbTaxonomySourceType.THEME, 19));
        var current = new SteamGameIgdbTerm(game, horror);
        when(terms.findBySourceTypeInAndIgdbTermIdIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(horror));
        when(relations.findBySteamAppIds(anyCollection())).thenReturn(List.of(current));

        new IgdbRawTaxonomyPersistenceService(terms, relations, jdbc)
                .syncBatch(Map.of(game, List.of(value(IgdbTaxonomySourceType.THEME, 19))));

        verify(terms).findBySourceTypeInAndIgdbTermIdIn(anyCollection(), anyCollection());
        verify(relations).findBySteamAppIds(Set.of(10L));
        verify(relations, never()).saveAll(any());
        verify(relations, never()).deleteAll(any());
        verify(jdbc, times(1)).batchUpdate(anyString(), anyCollection(), anyInt(),
                any(org.springframework.jdbc.core.ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void reusesTermsDeduplicatesRelationsAndKeepsGamesIsolated() {
        var terms = mock(IgdbTaxonomyTermRepository.class);
        var relations = mock(SteamGameIgdbTermRepository.class);
        var horror = new IgdbTaxonomyTerm(value(IgdbTaxonomySourceType.THEME, 19));
        when(terms.findBySourceTypeInAndIgdbTermIdIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(horror));
        when(terms.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(relations.findBySteamAppIds(anyCollection())).thenReturn(List.of());
        var first = new SteamGame(10, "A", 0, 0);
        var second = new SteamGame(20, "B", 0, 0);

        new IgdbRawTaxonomyPersistenceService(terms, relations).syncBatch(Map.of(
                first, List.of(value(IgdbTaxonomySourceType.THEME, 19),
                        value(IgdbTaxonomySourceType.THEME, 19)),
                second, List.of(value(IgdbTaxonomySourceType.KEYWORD, 416))));

        ArgumentCaptor<Iterable<SteamGameIgdbTerm>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(relations).saveAll(saved.capture());
        var list = new java.util.ArrayList<SteamGameIgdbTerm>();
        saved.getValue().forEach(list::add);
        assertThat(list).hasSize(2);
        assertThat(list).anySatisfy(r -> {
            assertThat(r.getGame().getSteamAppId()).isEqualTo(10);
            assertThat(r.getTaxonomyTerm().getIgdbTermId()).isEqualTo(19);
        }).anySatisfy(r -> {
            assertThat(r.getGame().getSteamAppId()).isEqualTo(20);
            assertThat(r.getTaxonomyTerm().getIgdbTermId()).isEqualTo(416);
        });
    }

    @Test
    void removesObsoleteRelationAndDoesNotRewriteCurrentOne() {
        var terms = mock(IgdbTaxonomyTermRepository.class);
        var relations = mock(SteamGameIgdbTermRepository.class);
        var game = new SteamGame(10, "A", 0, 0);
        var horror = new IgdbTaxonomyTerm(value(IgdbTaxonomySourceType.THEME, 19));
        var old = new SteamGameIgdbTerm(game,
                new IgdbTaxonomyTerm(value(IgdbTaxonomySourceType.KEYWORD, 416)));
        var current = new SteamGameIgdbTerm(game, horror);
        when(terms.findBySourceTypeInAndIgdbTermIdIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(horror));
        when(relations.findBySteamAppIds(anyCollection())).thenReturn(List.of(old, current));

        new IgdbRawTaxonomyPersistenceService(terms, relations)
                .syncBatch(Map.of(game, List.of(value(IgdbTaxonomySourceType.THEME, 19))));

        verify(relations).deleteAll(argThat(values -> values.iterator().next() == old));
        verify(relations, never()).saveAll(any());
    }

    @Test
    void repositoryFailureIsPropagatedForOwningTransactionToRollBack() {
        var terms = mock(IgdbTaxonomyTermRepository.class);
        var relations = mock(SteamGameIgdbTermRepository.class);
        when(terms.findBySourceTypeInAndIgdbTermIdIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        when(terms.saveAll(any())).thenThrow(new IllegalStateException("write failed"));
        var service = new IgdbRawTaxonomyPersistenceService(terms, relations);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.syncBatch(Map.of(new SteamGame(10, "A", 0, 0),
                        List.of(value(IgdbTaxonomySourceType.THEME, 19)))));
        verifyNoInteractions(relations);
    }

    private static IgdbTaxonomyValue value(IgdbTaxonomySourceType type, long id) {
        return new IgdbTaxonomyValue(type, id, "term-" + id, "term-" + id);
    }
}
