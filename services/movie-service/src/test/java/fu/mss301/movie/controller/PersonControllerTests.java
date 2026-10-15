package fu.mss301.movie.controller;

import fu.mss301.movie.entity.Person;
import fu.mss301.movie.exception.MovieExceptionHandler;
import fu.mss301.movie.exception.PersonExceptionHandler;
import fu.mss301.movie.repository.MovieCreditRepository;
import fu.mss301.movie.repository.PersonRepository;
import fu.mss301.movie.service.impl.PersonServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PersonControllerTests {
    private PersonRepository persons;
    private MovieCreditRepository credits;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        persons = mock(PersonRepository.class);
        credits = mock(MovieCreditRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(
            new PersonController(new PersonServiceImpl(persons, credits))
        ).setControllerAdvice(new PersonExceptionHandler(), new MovieExceptionHandler()).build();
    }

    private Person person() {
        Person person = new Person();
        person.setId(1);
        person.setFullName("Christopher Nolan");
        return person;
    }

    @Test
    void createTrimsNameAndAllowsDifferentPeopleWithSameName() throws Exception {
        AtomicInteger ids = new AtomicInteger();
        when(persons.saveAndFlush(any(Person.class))).thenAnswer(invocation -> {
            Person person = invocation.getArgument(0);
            person.setId(ids.incrementAndGet());
            return person;
        });
        for (int id = 1; id <= 2; id++) {
            mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"  Christopher Nolan  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/persons/" + id))
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.fullName").value("Christopher Nolan"));
        }
        verify(persons, times(2)).saveAndFlush(any(Person.class));
    }

    @Test
    void updateChangesManagedEntityAndFlushes() throws Exception {
        Person person = person();
        when(persons.findById(1)).thenReturn(Optional.of(person));
        mvc.perform(put("/api/persons/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"  Christopher Edward Nolan  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fullName").value("Christopher Edward Nolan"));
        assertEquals("Christopher Edward Nolan", person.getFullName());
        verify(persons).flush();
    }

    @Test
    void detailsAndMissingPersonUseResponseEnvelope() throws Exception {
        when(persons.findById(1)).thenReturn(Optional.of(person()));
        mvc.perform(get("/api/persons/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
        mvc.perform(get("/api/persons/2"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/persons/2").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"New name\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/persons/2")).andExpect(status().isNotFound());
    }

    @Test
    void existingCreditsBlockDeletion() throws Exception {
        when(persons.findById(1)).thenReturn(Optional.of(person()));
        when(credits.existsByPerson_Id(1)).thenReturn(true);
        mvc.perform(delete("/api/persons/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
        verify(persons, never()).delete(any(Person.class));
        verify(persons, never()).flush();
    }

    @Test
    void unusedPersonCanBeDeleted() throws Exception {
        Person person = person();
        when(persons.findById(1)).thenReturn(Optional.of(person));
        mvc.perform(delete("/api/persons/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200));
        verify(persons).delete(person);
        verify(persons).flush();
    }

    @Test
    void listUsesDefaultPaginationForBlankKeyword() throws Exception {
        when(persons.findAll(any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(person()), invocation.getArgument(0), 1));
        mvc.perform(get("/api/persons").param("keyword", " "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[0].fullName").value("Christopher Nolan"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void searchTrimsKeywordAndPreservesPagination() throws Exception {
        when(persons.findByFullNameContainingIgnoreCase(eq("Nolan"), any(Pageable.class)))
            .thenAnswer(invocation -> new PageImpl<>(List.of(person()), invocation.getArgument(1), 6));
        mvc.perform(get("/api/persons").param("keyword", " Nolan ")
            .param("page", "1").param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(5))
            .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"fullName\":\" \"}", "{\"fullName\":null}", "{", "null"})
    void invalidBodyReturns400WithoutWriting(String body) throws Exception {
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(persons, credits);
    }

    @Test
    void oversizedNameAndMissingBodyReturn400() throws Exception {
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"" + "a".repeat(256) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/persons").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(persons, credits);
    }

    @Test
    void invalidIdAndPaginationReturn400() throws Exception {
        mvc.perform(get("/api/persons/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/persons/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/persons").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/persons").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/persons").param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(persons, credits);
    }
}
