import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextField } from './text-field';

@Component({
  standalone: true,
  imports: [TextField, ReactiveFormsModule],
  template: `<app-text-field [label]="'Email'" [error]="error" [formControl]="ctrl" />`,
})
class Host {
  ctrl = new FormControl('');
  error = '';
}

describe('TextField', () => {
  it('ties the label to the input and renders an inline error with aria-invalid', async () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.error = 'Email is required';
    fixture.detectChanges();
    await fixture.whenStable();

    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(label.getAttribute('for')).toBe(input.id);

    const err = fixture.nativeElement.querySelector('.field__error') as HTMLElement;
    expect(err.textContent?.trim()).toBe('Email is required');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe(err.id);
  });

  it('writes a value from the control and propagates typing back (CVA)', async () => {
    const fixture = TestBed.createComponent(Host);
    const host = fixture.componentInstance;
    host.ctrl.setValue('a@b.com');
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('a@b.com');

    input.value = 'c@d.com';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(host.ctrl.value).toBe('c@d.com');
  });
});
