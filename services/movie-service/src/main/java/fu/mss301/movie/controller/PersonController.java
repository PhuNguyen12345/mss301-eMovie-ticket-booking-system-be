package fu.mss301.movie.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.dto.PageResponseDTO;
import fu.mss301.movie.dto.PersonRequestDTO;
import fu.mss301.movie.dto.PersonResponseDTO;
import fu.mss301.movie.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {
    private final PersonService personService;

    @GetMapping
    public ApiResponse<PageResponseDTO<PersonResponseDTO>> getPersons(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiResponse.success(personService.getPersons(keyword, page, size), "Persons retrieved successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<PersonResponseDTO> getPersonById(@PathVariable("id") Integer id) {
        return ApiResponse.success(personService.getPersonById(id), "Person retrieved successfully");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonResponseDTO>> createPerson(@RequestBody PersonRequestDTO request) {
        PersonResponseDTO person = personService.createPerson(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(person.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(201, "Person created successfully", person));
    }

    @PutMapping("/{id}")
    public ApiResponse<PersonResponseDTO> updatePerson(
        @PathVariable("id") Integer id, @RequestBody PersonRequestDTO request
    ) {
        return ApiResponse.success(personService.updatePerson(id, request), "Person updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePerson(@PathVariable("id") Integer id) {
        personService.deletePerson(id);
        return ApiResponse.success(null, "Person deleted successfully");
    }
}
