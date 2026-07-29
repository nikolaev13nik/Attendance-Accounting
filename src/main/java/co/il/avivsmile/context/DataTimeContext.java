package co.il.avivsmile.context;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import co.il.avivsmile.model.User;
import co.il.avivsmile.model.DataTime;
import co.il.avivsmile.dto.EditDataTimeUserDto;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTimeContext<R> {
    // input
    private Integer idUser;
    private Integer recordId;
    private LocalDate date;
    private LocalDate startDate;
    private LocalDate finishDate;
    private EditDataTimeUserDto editDto;

    // working state
    private User user;
    private DataTime dataTime;
    private List<DataTime> dataTimeList;

    // output
    private R result;
}