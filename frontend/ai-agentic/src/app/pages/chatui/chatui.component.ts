import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { MarkdownComponent } from 'ngx-markdown';
import { FileService } from '../../services/file.service';
import { DocSource } from '../../interfaces/doc-source';
import { ImgSource } from '../../interfaces/img-source';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { chatUrl } from '../../../constants';

@Component({
  selector: 'app-chatui',
  standalone: true,
  imports: [
    MarkdownComponent,
    FormsModule,
    CommonModule
  ],
  templateUrl: './chatui.component.html',
  styleUrl: './chatui.component.css'
})
export class ChatuiComponent {


  question: any;
  response: any;
  selectedFile!: File;
  message = '';
  sourcesDocs: DocSource[] = [];
  sourcesImgs: ImgSource[] = [];

  constructor(
    private http: HttpClient,
    private fileService: FileService
  ) {
    //
  }

  async askAgent() {
    this.response = "";
    this.sourcesDocs = [];
    this.sourcesImgs = [];

    this.http.get(chatUrl, {
      params: {question: this.question},
      responseType: 'text'
    }).subscribe({
      next: (raw: string) => {
        console.log("Response: ", raw);

        try {
          let parsed = JSON.parse(raw);

          if(typeof parsed === "string") {
            parsed = JSON.parse(parsed);
          }

          this.sourcesDocs = parsed.documents ?? [];
          this.sourcesImgs = parsed.images ?? [];
          this.response = [
            this.response,
            ...this.sourcesDocs.map(d => d.text)
          ].join("\n\n");

        }
        catch(err) {
          console.warn("Error parse response:", err);
          this.response = raw; // fallback 
        }
      },
      error: (err) => console.error("HTTP Error: ", err)
    });
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  onSubmit() {
    const formData = new FormData();
    formData.append('file', this.selectedFile);
    this.fileService.sendFile(formData).subscribe({
      next: (res: any) => console.log("File stored successfully", res),
      error: (err: any) => console.log("Error occured: ", err)
    });
  }


}
