package gamelife.character;

import gamelife.ledger.LedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CharacterController {
    private final CharacterService service;
    private final LedgerService ledger;

    public CharacterController(CharacterService service, LedgerService ledger) {
        this.service = service;
        this.ledger = ledger;
    }

    @GetMapping("/api/character")
    public CharacterService.Character character() {
        return service.compute(ledger.all());
    }
}
