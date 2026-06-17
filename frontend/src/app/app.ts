import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root — the router renders /login or the authenticated shell into this outlet (Story 9.2). */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
