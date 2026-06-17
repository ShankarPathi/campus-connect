import { Routes } from '@angular/router';
import { RecruiterHome } from './recruiter-home';

/** Recruiter portal routes (Story 9.2 stubs — a later story defines the real child screens). */
export const RECRUITER_ROUTES: Routes = [{ path: '**', component: RecruiterHome }];
