package fu.mss301.cinema;

import fu.mss301.cinema.application.port.in.CinemaCatalog;
import fu.mss301.cinema.application.port.in.CinemaManagement;
import fu.mss301.cinema.domain.model.*;
import fu.mss301.cinema.infrastructure.web.controller.CinemaController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CinemaControllerContractTests {
    @Test
    void existingFeignClientsReceiveExpectedFields() throws Exception {
        CinemaCatalog catalog = mock(CinemaCatalog.class);
        when(catalog.getCinema(1)).thenReturn(new Cinema(1, "C1", "Central", "Ha Noi", FacilityStatus.ACTIVE));
        when(catalog.getScreen(7)).thenReturn(new Screen(7, 1, "S1", "Screen 1", FacilityStatus.ACTIVE));
        when(catalog.listSeats(7)).thenReturn(List.of(new Seat(9, 7, "A", 1, 2, FacilityStatus.ACTIVE)));
        var mvc = MockMvcBuilders.standaloneSetup(new CinemaController(catalog, mock(CinemaManagement.class))).build();

        mvc.perform(get("/api/cinemas/1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.location").value("Ha Noi"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/api/screens/7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.cinemaId").value(1));
        mvc.perform(get("/api/screens/7/seats"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].seatTypeId").value(2))
            .andExpect(jsonPath("$.data[0].rowCode").value("A"));
    }
}
