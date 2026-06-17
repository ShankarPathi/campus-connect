import { Routes } from '@angular/router';
import { StudentHome } from './student-home';

/** Student portal routes (Story 9.2 stubs — a later story defines the real child screens). */
export const STUDENT_ROUTES: Routes = [{ path: '**', component: StudentHome }];
