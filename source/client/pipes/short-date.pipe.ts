import { Pipe, PipeTransform } from '@angular/core';

const IDX_YEAR: number = 0;
const IDX_MONTH: number = 1;
const IDX_DATE: number = 2;
const IDX_HOUR: number = 3;
const IDX_MINUTES: number = 4;
const months: Array<string> = [ 'Null', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'July', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

@Pipe({
  name: 'shortDate'
})
export class ShortDatePipe implements PipeTransform {

  /**
   * Takes a Java 8 JSR 310 serialised LocalDateTime object, and outputs a date string, minus the time elements.
   * @param dateTime 
   * @param args 
   */
  transform( dateTime: any, args?: any): string {
    let result: string = "";
    if ( dateTime) {
      result += dateTime[IDX_DATE] + ' ' + months[dateTime[IDX_MONTH]] + ' ' + dateTime[IDX_YEAR];
    }
    else {
      let now = new Date();
      now.getDate();
      result += now.getDate() + ' ' + months[now.getMonth() + 1] + ' ' + now.getFullYear();
    }
    return result;
  }

}

