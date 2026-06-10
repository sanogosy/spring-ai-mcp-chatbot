import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { apiUrlDocument } from '../../constants';

@Injectable({
  providedIn: 'root'
})
export class FileService {

  constructor(private httpClient: HttpClient) { }

  sendFile(formData: FormData) {
    return this.httpClient.post(apiUrlDocument, formData);
  }
}
